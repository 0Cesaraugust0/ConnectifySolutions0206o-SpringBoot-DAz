package com.connectify.service;

import com.connectify.entity.Ticket;
import com.connectify.entity.PurchaseStatus;
import com.connectify.repository.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.time.Instant;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;

    public TicketService(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    public Optional<Ticket> findByCode(String code) {
        if (code == null || code.isBlank() || code.length() > 255) {
            return Optional.empty();
        }
        return ticketRepository.findByCode(code.trim());
    }

    @Transactional(readOnly = true)
    public GateValidationResult consultTicketForEvent(String code, Long eventId) {
        Optional<Ticket> ticket = findByCode(code);
        if (ticket.isEmpty()) return GateValidationResult.notFound(code);
        GateValidationResult refusal = refusal(ticket.get(), eventId);
        return refusal == null ? GateValidationResult.available(ticket.get()) : refusal;
    }

    @Transactional
    public GateValidationResult validateTicketForEvent(String code, Long eventId, String gateName) {
        if (code == null || code.isBlank() || code.length() > 255) return GateValidationResult.notFound(code);
        Optional<Ticket> ticketOptional = ticketRepository.findLockedByCode(code.trim());

        if (ticketOptional.isEmpty()) {
            return GateValidationResult.notFound(code);
        }

        Ticket ticket = ticketOptional.get();

        GateValidationResult refusal = refusal(ticket, eventId);
        if (refusal != null) return refusal;

        ticket.setUsed(true);
        ticket.setUsedAt(Instant.now());
        ticket.setUsedGateName(gateName);
        ticketRepository.save(ticket);
        return GateValidationResult.valid(ticket);
    }

    private GateValidationResult refusal(Ticket ticket, Long eventId) {
        if (ticket.getEvent() == null || !ticket.getEvent().getId().equals(eventId)) return GateValidationResult.wrongEvent(ticket);
        if (!EventGateAccessService.allowsEntry(ticket.getEvent())) return GateValidationResult.eventClosed(ticket.getCode());
        if (ticket.getPurchase() == null || ticket.getPurchase().getStatus() != PurchaseStatus.COMPLETED)
            return GateValidationResult.notPaid(ticket.getCode());
        if (ticket.isUsed()) return GateValidationResult.alreadyUsed(ticket);
        return null;
    }

    public static class GateValidationResult {
        private final String status;
        private final String message;
        private final Ticket ticket;
        private final String code;

        private GateValidationResult(String status, String message, Ticket ticket, String code) {
            this.status = status;
            this.message = message;
            this.ticket = ticket;
            this.code = code;
        }

        public static GateValidationResult valid(Ticket ticket) {
            return new GateValidationResult("VALID", "Ticket válido. Acceso autorizado.", ticket, ticket.getCode());
        }

        public static GateValidationResult available(Ticket ticket) {
            return new GateValidationResult("AVAILABLE", "Entrada disponible. La consulta no registra el ingreso.", ticket, ticket.getCode());
        }

        public static GateValidationResult notPaid(String code) {
            return new GateValidationResult("NOT_PAID", "La compra no está confirmada o la entrada fue cancelada o reembolsada.", null, code);
        }

        public static GateValidationResult eventClosed(String code) {
            return new GateValidationResult("EVENT_CLOSED", "El evento no está habilitado para registrar ingresos.", null, code);
        }

        public static GateValidationResult alreadyUsed(Ticket ticket) {
            return new GateValidationResult("USED", "Ticket encontrado, pero ya fue usado anteriormente.", ticket, ticket.getCode());
        }

        public static GateValidationResult wrongEvent(Ticket ticket) {
            return new GateValidationResult("WRONG_EVENT", "La entrada pertenece a otro evento.", null, ticket.getCode());
        }

        public static GateValidationResult notFound(String code) {
            return new GateValidationResult("NOT_FOUND", "Ticket no encontrado. Código inválido.", null, code);
        }

        public String getStatus() {
            return status;
        }

        public String getMessage() {
            return message;
        }

        public Ticket getTicket() {
            return ticket;
        }

        public String getCode() {
            return code;
        }
    }
}
