package com.connectify.service;

import com.connectify.config.IzipayWebProperties;
import com.connectify.dto.*;
import com.connectify.entity.*;
import com.connectify.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IzipayWebCheckoutServiceTest {
    private final IzipayWebAttemptRepository attempts = mock(IzipayWebAttemptRepository.class);
    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final EventRepository events = mock(EventRepository.class);
    private final TicketTypeRepository types = mock(TicketTypeRepository.class);
    private final PurchaseRepository purchases = mock(PurchaseRepository.class);
    private final IzipayWebGateway gateway = mock(IzipayWebGateway.class);
    private final IzipayWebSignature signatures = mock(IzipayWebSignature.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private IzipayWebCheckoutService service;
    private IzipayWebAttempt attempt;
    private Event event;
    private final String email = "client@example.test";

    @BeforeEach
    void setup() throws Exception {
        IzipayWebProperties properties = new IzipayWebProperties();
        properties.setMerchantCode("1234567"); properties.setApiKey("fixture-api");
        properties.setPublicKey("fixture-public"); properties.setHashKey("fixture-hash");
        properties.setPaymentMethods("CARD,YAPE_CODE,PAGO_PUSH,QR");
        service = new IzipayWebCheckoutService(properties, attempts, users, events, types, purchases, gateway,
                signatures, new IzipayWebPaymentValidator(), mapper);
        event = new Event(); ReflectionTestUtils.setField(event, "id", 1L);
        event.setTitle("Test Event"); event.setPrice(new BigDecimal("100.00")); event.setCapacity(100); event.setSold(0);
        event.setStatus(EventStatus.PUBLISHED); event.setEventDate(LocalDateTime.now().plusDays(3));
        when(events.findById(1L)).thenReturn(Optional.of(event));
        when(events.findLockedById(1L)).thenReturn(Optional.of(event));
        when(users.findLockedByEmail(email)).thenReturn(Optional.of(new UserAccount("Test Client", email, "fixture", Role.CLIENT)));
        when(signatures.verificationToken(anyString(), anyString())).thenReturn("fixture-token");
        when(signatures.sameHash(eq("fixture-token"), anyString())).thenReturn(true);
        when(gateway.authorize(any())).thenReturn("fixture-authorization");
        when(purchases.save(any())).thenAnswer(invocation -> {
            Purchase value = invocation.getArgument(0); ReflectionTestUtils.setField(value, "id", 42L); return value;
        });
        attempt = new IzipayWebAttempt();
        attempt.setOrderNumber("0123456789ABCDE"); attempt.setTransactionId("transaction123");
        attempt.setBuyerEmail(email); attempt.setMerchantCode("1234567");
        attempt.setPaymentMethod("CARD");
        attempt.setAmount(new BigDecimal("105.00")); attempt.setSubtotal(new BigDecimal("100.00")); attempt.setServiceFee(new BigDecimal("5.00"));
        attempt.setSnapshotJson(mapper.writeValueAsString(List.of(new IzipayWebLine(1L, null, "Test Event", "General", 1, new BigDecimal("100.00")))));
        attempt.setBillingJson(mapper.writeValueAsString(buyer().billing(email)));
        attempt.setVerificationTokenHash("stored-hash");
        when(attempts.findLocked(attempt.getOrderNumber())).thenReturn(Optional.of(attempt));
        when(attempts.findById(attempt.getOrderNumber())).thenReturn(Optional.of(attempt));
        when(signatures.verify(any())).thenReturn(new IzipayWebSignature.VerifiedPayload(IzipayWebSecurityTest.payload("105.00", attempt.getOrderNumber()), "fixture-digest"));
    }

    @Test
    void callbackAndIpnRetriesGenerateOnePurchaseUsingDurableSnapshot() {
        Map<String, Object> first = service.verify(email, attempt.getOrderNumber(), "fixture-token", mapper.createObjectNode());
        Map<String, Object> duplicate = service.notification(mapper.createObjectNode());
        assertThat(first.get("redirectUrl")).isEqualTo("/cart/confirmation/42");
        assertThat(duplicate).isEqualTo(first);
        assertThat(event.getSold()).isEqualTo(1);
        verify(purchases, times(1)).save(argThat(value -> value.getTickets().size() == 1 && value.getTotal().compareTo(new BigDecimal("105.00")) == 0));
    }

    @Test
    void notificationCompletesWithoutBrowserSessionAndOwnerRecoversStatus() {
        service.notification(mapper.createObjectNode());
        assertThat(service.status(email, attempt.getOrderNumber()).get("status")).isEqualTo("COMPLETED");
        assertThatThrownBy(() -> service.status("other@example.test", attempt.getOrderNumber())).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void wrongOwnerOrVerificationTokenNeverCreatesPurchase() {
        assertThatThrownBy(() -> service.verify("other@example.test", attempt.getOrderNumber(), "fixture-token", mapper.createObjectNode())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.verify(email, attempt.getOrderNumber(), "bad-token", mapper.createObjectNode())).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(purchases);
    }

    @Test
    void verifiedPaymentWithExhaustedInventoryIsRetainedForReview() {
        event.setSold(100);
        assertThat(service.notification(mapper.createObjectNode()).get("status")).isEqualTo("REVIEW");
        assertThat(attempt.getSignedPayloadHash()).isEqualTo("fixture-digest");
        verifyNoInteractions(purchases);
    }

    @Test
    void startDoesNotChargeOrIssueTicketsAndStoresSnapshot() {
        Map<String, Object> response = service.start(email, buyer(), cart(), "YAPE_CODE");
        assertThat(response.get("authorization")).isEqualTo("fixture-authorization");
        assertThat(response.get("paymentMethod")).isEqualTo("YAPE_CODE");
        assertThat(((Map<?, ?>) ((Map<?, ?>) response.get("config")).get("order")).get("payMethod")).isEqualTo("YAPE_CODE");
        verify(attempts).save(argThat(value -> value.getOrderNumber().length() == 15
                && value.getAmount().compareTo(new BigDecimal("105.00")) == 0
                && "YAPE_CODE".equals(value.getPaymentMethod())
                && value.getSnapshotJson().contains("Test Event")));
        verifyNoInteractions(purchases);
    }

    @Test
    void pendingOrderIsReplacedWhenCustomerRetakesPayment() throws Exception {
        Map<String, Object> first = service.start(email, buyer(), cart(), "CARD");
        var capture = org.mockito.ArgumentCaptor.forClass(IzipayWebAttempt.class);
        verify(attempts).save(capture.capture());
        IzipayWebAttempt saved = capture.getValue();
        when(attempts.findByBuyerEmailIgnoreCaseAndStatusOrderByCreatedAtDesc(email, "PENDING")).thenReturn(List.of(saved));
        Map<String, Object> resumed = service.start(email, buyer(), cart(), "QR");
        assertThat(resumed.get("orderNumber")).isNotEqualTo(first.get("orderNumber"));
        assertThat(saved.getStatus()).isEqualTo("ABANDONED");
        verify(gateway, times(2)).authorize(any());
        verify(attempts, times(2)).save(any());
    }

    @Test
    void lateApprovalForReplacedOrderGoesToReviewWithoutDuplicateTickets() {
        attempt.setStatus("ABANDONED");
        assertThat(service.notification(mapper.createObjectNode()).get("status")).isEqualTo("REVIEW");
        assertThat(attempt.getReviewReason()).contains("reemplazado");
        verifyNoInteractions(purchases);
    }

    @Test
    void staleCartPriceCannotSetGatewayAmount() {
        List<CartItem> changed = cart(); changed.get(0).setUnitPrice(BigDecimal.ONE);
        assertThatThrownBy(() -> service.start(email, buyer(), changed, "CARD")).hasMessageContaining("precio");
        verifyNoInteractions(gateway);
    }

    @Test
    void rejectsPaymentMethodThatIsNotEnabled() {
        assertThatThrownBy(() -> service.start(email, buyer(), cart(), "APPLE_PAY"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no está habilitado");
        verifyNoInteractions(gateway);
    }

    @Test
    void ambiguousSignedCommunicationErrorDoesNotUnlockASecondCharge() throws Exception {
        String raw = IzipayWebSecurityTest.payload("105.00", attempt.getOrderNumber()).toString()
                .replace("\"code\":\"00\"", "\"code\":\"EC\"").replace("AUTH123", "");
        when(signatures.verify(any())).thenReturn(new IzipayWebSignature.VerifiedPayload(mapper.readTree(raw), "fixture-digest"));
        assertThat(service.notification(mapper.createObjectNode()).get("status")).isEqualTo("PENDING");
        verifyNoInteractions(purchases);
    }

    private List<CartItem> cart() { return new ArrayList<>(List.of(new CartItem(1L, null, "General", "Test Event", "", "Lima", 1, new BigDecimal("100.00")))); }
    private IzipayWebBuyer buyer() { return new IzipayWebBuyer("Test", "Client", "12345678", "999999999", "Avenida Peru 100", "Lima", "Lima", "15001"); }
}
