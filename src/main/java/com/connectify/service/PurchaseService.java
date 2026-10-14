package com.connectify.service;

import com.connectify.dto.CartItem;
import com.connectify.entity.Event;
import com.connectify.entity.Purchase;
import com.connectify.entity.PurchaseStatus;
import com.connectify.entity.Ticket;
import com.connectify.repository.EventRepository;
import com.connectify.repository.PurchaseRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class PurchaseService {

    private final PurchaseRepository purchaseRepository;
    private final EventRepository eventRepository;
    private final CartService cartService;

    public PurchaseService(PurchaseRepository purchaseRepository, EventRepository eventRepository, CartService cartService) {
        this.purchaseRepository = purchaseRepository;
        this.eventRepository = eventRepository;
        this.cartService = cartService;
    }

    @Transactional
    public Purchase completePurchase(HttpSession session, String firstName, String lastName, String dni, String phone, String email) {
        List<CartItem> items = cartService.getItems(email);
        if (items.isEmpty()) {
            throw new IllegalStateException("No hay entradas en el carrito");
        }

        Purchase purchase = new Purchase();
        purchase.setBuyerFirstName(firstName);
        purchase.setBuyerLastName(lastName);
        purchase.setBuyerDni(dni);
        purchase.setBuyerPhone(phone);
        purchase.setBuyerEmail(email);
        purchase.setSubtotal(cartService.subtotal(email));
        purchase.setServiceFee(cartService.serviceFee(email));
        purchase.setTotal(cartService.total(email));
        purchase.setStatus(PurchaseStatus.COMPLETED);

        String attendeeName = firstName + " " + lastName;

        for (CartItem item : items) {
            Event event = eventRepository.findById(item.getEventId())
                    .orElseThrow(() -> new IllegalArgumentException("Evento no encontrado"));

            for (int i = 0; i < item.getQuantity(); i++) {
                Ticket ticket = new Ticket();
                ticket.setEvent(event);
                ticket.setAttendeeName(attendeeName);
                ticket.setAttendeeEmail(email);
                ticket.setCode(generateTicketCode(event.getId()));
                purchase.addTicket(ticket);
            }

            Integer sold = event.getSold() != null ? event.getSold() : 0;
            event.setSold(sold + item.getQuantity());
            eventRepository.save(event);
        }

        Purchase saved = purchaseRepository.save(purchase);
        cartService.clear(email);
        return saved;
    }

    public Purchase findById(Long id) {
        return purchaseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Compra no encontrada"));
    }

    private String generateTicketCode(Long eventId) {
        return "CNF-" + eventId + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
