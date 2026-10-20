package com.connectify.controller;

import com.connectify.config.IzipayProperties;
import com.connectify.config.IzipayWebProperties;
import com.connectify.dto.CartItem;
import com.connectify.entity.Event;
import com.connectify.entity.EventStatus;
import com.connectify.entity.Role;
import com.connectify.entity.UserAccount;
import com.connectify.repository.TicketTypeRepository;
import com.connectify.repository.UserAccountRepository;
import com.connectify.service.CartService;
import com.connectify.service.EventService;
import com.connectify.service.IzipayPaymentService;
import com.connectify.service.PurchaseService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CartDemonstrationSafetyTest {
    private final CartService cart = mock(CartService.class);
    private final EventService events = mock(EventService.class);
    private final PurchaseService purchases = mock(PurchaseService.class);
    private final IzipayPaymentService legacyGateway = mock(IzipayPaymentService.class);
    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final CartController controller = new CartController(cart, events, purchases, legacyGateway,
            mock(IzipayProperties.class), mock(TicketTypeRepository.class), accounts,
            mock(IzipayWebProperties.class));

    @Test
    void directCartPostCannotAddDemonstrationEventWithoutTicketVariant() {
        when(events.findById(22L)).thenReturn(Optional.of(demoEvent()));

        assertThatThrownBy(() -> controller.add(22L, 1, null, new MockHttpSession()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("demostrativo");
        verifyNoInteractions(cart);
    }

    @Test
    void legacyPaymentCannotCreateTokenForStaleDemonstrationCart() {
        MockHttpSession session = new MockHttpSession();
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("client@example.test");
        when(accounts.findByEmailIgnoreCase("client@example.test"))
                .thenReturn(Optional.of(new UserAccount("Client", "client@example.test", "fixture", Role.CLIENT)));
        when(cart.getItems(session)).thenReturn(List.of(new CartItem(22L, null, "General", "Demo", "", "Lima", 1, BigDecimal.TEN)));
        when(events.findById(22L)).thenReturn(Optional.of(demoEvent()));

        assertThatThrownBy(() -> controller.startIzipayCheckout("Cliente", "Ejemplo", "12345678", "999999999",
                session, auth, mock(RedirectAttributes.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("demostrativo");
        verify(legacyGateway, never()).createPaymentToken(any());
    }

    private Event demoEvent() {
        Event event = new Event();
        ReflectionTestUtils.setField(event, "id", 22L);
        event.setStatus(EventStatus.PUBLISHED);
        event.setOrganizerEmail("org@connectify.com");
        return event;
    }
}
