package com.connectify.controller;

import com.connectify.dto.CartItem;
import com.connectify.service.CartService;
import com.connectify.service.IzipayWebCheckoutService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class IzipayWebControllerTest {
    private final IzipayWebCheckoutService service = mock(IzipayWebCheckoutService.class);
    private final CartService cart = mock(CartService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new IzipayWebController(service, cart)).build();

    @Test
    void startBindsFormDataAndRequiresMatchingCheckoutToken() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String token = IzipayWebController.requestToken(session);
        var account = UsernamePasswordAuthenticationToken.authenticated("client@example.test", "ignored", List.of());
        when(cart.getItems("client@example.test")).thenReturn(new ArrayList<>(List.of(new CartItem(1L, "Event", "", "", 1, BigDecimal.TEN))));
        when(service.start(eq("client@example.test"), any(), any(), eq("YAPE_CODE"))).thenReturn(Map.of("orderNumber", "0123456789ABCDE"));
        mvc.perform(post("/cart/checkout/izipay/session").session(session).principal(account)
                .param("checkoutToken", "wrong-token").param("payMethod", "YAPE_CODE").param("firstName", "Test").param("lastName", "Client"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
        mvc.perform(post("/cart/checkout/izipay/session").session(session).principal(account)
                .param("checkoutToken", token).param("payMethod", "YAPE_CODE").param("firstName", "Test").param("lastName", "Client")
                .param("dni", "12345678").param("phone", "999999999").param("street", "Avenida Peru 100")
                .param("city", "Lima").param("state", "Lima").param("postalCode", "15001"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.orderNumber").value("0123456789ABCDE"));
        verify(service).start(eq("client@example.test"), argThat(buyer -> buyer.firstName().equals("Test") && buyer.postalCode().equals("15001")), any(), eq("YAPE_CODE"));
    }

    @Test
    void lostHttpSessionCannotStartPaymentUsingOldPageToken() throws Exception {
        var account = UsernamePasswordAuthenticationToken.authenticated("client@example.test", "ignored", List.of());
        mvc.perform(post("/cart/checkout/izipay/session").session(new MockHttpSession()).principal(account)
                .param("checkoutToken", "from-an-expired-session").param("payMethod", "CARD").param("firstName", "Test").param("lastName", "Client"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
