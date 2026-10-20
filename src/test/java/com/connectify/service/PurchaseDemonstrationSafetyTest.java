package com.connectify.service;

import com.connectify.dto.CartItem;
import com.connectify.entity.Event;
import com.connectify.repository.EventRepository;
import com.connectify.repository.PurchaseRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class PurchaseDemonstrationSafetyTest {
    private final PurchaseRepository purchases = mock(PurchaseRepository.class);
    private final EventRepository events = mock(EventRepository.class);
    private final CartService cart = mock(CartService.class);
    private final PurchaseService service = new PurchaseService(purchases, events, cart);

    @Test
    void legacyCompletionCannotIssueTicketsForDemonstrationEvent() {
        Event event = new Event();
        event.setOrganizerEmail("org@connectify.com");
        when(events.findById(22L)).thenReturn(Optional.of(event));
        when(cart.getItems("client@example.test")).thenReturn(List.of(
                new CartItem(22L, null, "General", "Demo", "", "Lima", 1, BigDecimal.TEN)));

        assertThatThrownBy(() -> service.completePurchase(null, "Cliente", "Ejemplo", "12345678",
                "999999999", "client@example.test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("demostrativo");
        verifyNoInteractions(purchases);
        verify(cart, never()).clear("client@example.test");
    }
}
