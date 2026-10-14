package com.connectify.service;

import com.connectify.dto.IzipayWebLine;
import com.connectify.entity.Event;
import com.connectify.entity.EventStatus;
import com.connectify.entity.ShoppingCart;
import com.connectify.entity.ShoppingCartConsumption;
import com.connectify.entity.TicketType;
import com.connectify.entity.UserAccount;
import com.connectify.repository.EventRepository;
import com.connectify.repository.ShoppingCartConsumptionRepository;
import com.connectify.repository.ShoppingCartRepository;
import com.connectify.repository.TicketTypeRepository;
import com.connectify.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CartServiceTest {
    private final ShoppingCartRepository carts = mock(ShoppingCartRepository.class);
    private final ShoppingCartConsumptionRepository consumptions = mock(ShoppingCartConsumptionRepository.class);
    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final EventRepository events = mock(EventRepository.class);
    private final TicketTypeRepository ticketTypes = mock(TicketTypeRepository.class);
    private final CartService service = new CartService(carts, consumptions, users, events, ticketTypes);

    @Test
    void cartBelongsToAccountAndCanBeLoadedWithoutOriginalHttpSession() {
        UserAccount buyer = new UserAccount();
        buyer.setEmail("client@example.test");
        Event event = event(10L);
        TicketType type = ticketType(20L, event);
        ShoppingCart cart = new ShoppingCart();
        cart.setBuyer(buyer);

        when(carts.findLockedByBuyerEmail("client@example.test")).thenReturn(Optional.of(cart));
        when(users.findLockedByEmail("client@example.test")).thenReturn(Optional.of(buyer));
        when(carts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(events.findById(10L)).thenReturn(Optional.of(event));
        when(ticketTypes.findById(20L)).thenReturn(Optional.of(type));

        service.addEvent("CLIENT@example.test", event, type, 1);
        service.addEvent("client@example.test", event, type, 2);
        when(carts.findByBuyer_EmailIgnoreCase("client@example.test")).thenReturn(Optional.of(cart));

        var restored = service.getItems("client@example.test");
        assertEquals(1, restored.size());
        assertEquals(3, restored.get(0).getQuantity());
        assertEquals(new BigDecimal("55.00"), restored.get(0).getUnitPrice());
    }

    @Test
    void paidOrderRemovesItsLinesOnlyOnce() {
        Event event = event(10L);
        TicketType type = ticketType(20L, event);
        ShoppingCart cart = new ShoppingCart();
        UserAccount buyer = new UserAccount();
        buyer.setEmail("client@example.test");
        cart.setBuyer(buyer);
        when(carts.findLockedByBuyerEmail("client@example.test")).thenReturn(Optional.of(cart));
        when(carts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(users.findLockedByEmail("client@example.test")).thenReturn(Optional.of(buyer));
        when(events.findById(10L)).thenReturn(Optional.of(event));
        when(ticketTypes.findById(20L)).thenReturn(Optional.of(type));
        service.addEvent("client@example.test", event, type, 3);
        when(consumptions.existsById("ORDER-1")).thenReturn(false, true);
        IzipayWebLine paid = new IzipayWebLine(10L, 20L, "Evento", "General", 2, new BigDecimal("55.00"));

        service.removePurchased("client@example.test", "ORDER-1", List.of(paid));
        service.removePurchased("client@example.test", "ORDER-1", List.of(paid));

        assertEquals(1, cart.getItems().get(0).getQuantity());
        verify(consumptions, times(1)).save(any(ShoppingCartConsumption.class));
    }

    private Event event(Long id) {
        Event event = new Event("Evento", "", null, LocalDateTime.now().plusDays(2),
                "Lima", "Lima", new BigDecimal("55.00"), 100, 0, "", false, EventStatus.PUBLISHED);
        ReflectionTestUtils.setField(event, "id", id);
        return event;
    }

    private TicketType ticketType(Long id, Event event) {
        TicketType type = new TicketType();
        ReflectionTestUtils.setField(type, "id", id);
        type.setEvent(event);
        type.setName("General");
        type.setPrice(new BigDecimal("55.00"));
        type.setQuantityAvailable(100);
        return type;
    }
}
