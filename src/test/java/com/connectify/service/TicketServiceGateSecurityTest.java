package com.connectify.service;

import com.connectify.entity.Event;
import com.connectify.entity.EventStatus;
import com.connectify.entity.Purchase;
import com.connectify.entity.PurchaseStatus;
import com.connectify.entity.Ticket;
import com.connectify.repository.TicketRepository;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TicketServiceGateSecurityTest {
    private final TicketRepository tickets = mock(TicketRepository.class);
    private final TicketService service = new TicketService(tickets);

    private Ticket ticket(long eventId) {
        Event event = new Event();
        ReflectionTestUtils.setField(event, "id", eventId);
        event.setStatus(EventStatus.PUBLISHED);
        Purchase purchase = new Purchase();
        purchase.setStatus(PurchaseStatus.COMPLETED);
        Ticket ticket = new Ticket();
        ticket.setCode("QR-TEST-UNICO");
        ticket.setEvent(event);
        ticket.setPurchase(purchase);
        ticket.setAttendeeName("Cliente de prueba");
        ticket.setAttendeeEmail("cliente@example.test");
        return ticket;
    }

    @Test void consultationDoesNotConsumeTheTicketOrWriteAttendance() {
        Ticket ticket = ticket(22L);
        when(tickets.findByCode(ticket.getCode())).thenReturn(Optional.of(ticket));

        var result = service.consultTicketForEvent("  QR-TEST-UNICO  ", 22L);

        assertThat(result.getStatus()).isEqualTo("AVAILABLE");
        assertThat(result.getTicket()).isSameAs(ticket);
        assertThat(ticket.isUsed()).isFalse();
        assertThat(ticket.getUsedAt()).isNull();
        assertThat(ticket.getUsedGateName()).isNull();
        verify(tickets, never()).save(any());
        verify(tickets, never()).findLockedByCode(any());
    }

    @Test void admissionLocksTheQrAndRecordsTheTimeAndDoor() {
        Ticket ticket = ticket(22L);
        when(tickets.findLockedByCode(ticket.getCode())).thenReturn(Optional.of(ticket));
        Instant before = Instant.now();

        var result = service.validateTicketForEvent(" QR-TEST-UNICO ", 22L, "Puerta norte");

        assertThat(result.getStatus()).isEqualTo("VALID");
        assertThat(ticket.isUsed()).isTrue();
        assertThat(ticket.getUsedAt()).isBetween(before, Instant.now());
        assertThat(ticket.getUsedGateName()).isEqualTo("Puerta norte");
        verify(tickets).findLockedByCode(ticket.getCode());
        verify(tickets, never()).findByCode(any());
        verify(tickets).save(ticket);
    }

    @Test void secondDoorCannotAdmitATicketAlreadyConsumedAtTheFirstDoor() {
        Ticket ticket = ticket(22L);
        when(tickets.findLockedByCode(ticket.getCode())).thenReturn(Optional.of(ticket));
        assertThat(service.validateTicketForEvent(ticket.getCode(), 22L, "Puerta 1").getStatus()).isEqualTo("VALID");
        Instant admittedAt = ticket.getUsedAt();

        var result = service.validateTicketForEvent(ticket.getCode(), 22L, "Puerta 2");

        assertThat(result.getStatus()).isEqualTo("USED");
        assertThat(ticket.getUsedAt()).isEqualTo(admittedAt);
        assertThat(ticket.getUsedGateName()).isEqualTo("Puerta 1");
        verify(tickets, times(1)).save(ticket);
    }

    @Test void ticketFromAnotherEventNeverRevealsItsOwnerOrMutatesAttendance() {
        Ticket ticket = ticket(99L);
        when(tickets.findByCode(ticket.getCode())).thenReturn(Optional.of(ticket));
        when(tickets.findLockedByCode(ticket.getCode())).thenReturn(Optional.of(ticket));

        var consultation = service.consultTicketForEvent(ticket.getCode(), 22L);
        var admission = service.validateTicketForEvent(ticket.getCode(), 22L, "Puerta 1");

        assertThat(consultation.getStatus()).isEqualTo("WRONG_EVENT");
        assertThat(admission.getStatus()).isEqualTo("WRONG_EVENT");
        assertThat(consultation.getTicket()).isNull();
        assertThat(admission.getTicket()).isNull();
        assertThat(consultation.getMessage()).doesNotContain(ticket.getAttendeeName(), ticket.getAttendeeEmail());
        assertThat(admission.getMessage()).doesNotContain(ticket.getAttendeeName(), ticket.getAttendeeEmail());
        assertThat(ticket.isUsed()).isFalse();
        verify(tickets, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = PurchaseStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "COMPLETED")
    void pendingCancelledAndRefundedPurchasesCannotEnter(PurchaseStatus status) {
        Ticket ticket = ticket(22L);
        ticket.getPurchase().setStatus(status);
        when(tickets.findByCode(ticket.getCode())).thenReturn(Optional.of(ticket));
        when(tickets.findLockedByCode(ticket.getCode())).thenReturn(Optional.of(ticket));

        assertThat(service.consultTicketForEvent(ticket.getCode(), 22L).getStatus()).isNotIn("AVAILABLE", "VALID");
        assertThat(service.validateTicketForEvent(ticket.getCode(), 22L, "Puerta 1").getStatus()).isNotIn("AVAILABLE", "VALID");
        assertThat(ticket.isUsed()).isFalse();
        verify(tickets, never()).save(any());
    }

    @Test void aQrWithoutAConfirmedPurchaseCannotEnter() {
        Ticket ticket = ticket(22L);
        ticket.setPurchase(null);
        when(tickets.findLockedByCode(ticket.getCode())).thenReturn(Optional.of(ticket));

        assertThat(service.validateTicketForEvent(ticket.getCode(), 22L, "Puerta 1").getStatus()).isNotEqualTo("VALID");
        assertThat(ticket.isUsed()).isFalse();
        verify(tickets, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = EventStatus.class, mode = EnumSource.Mode.EXCLUDE, names = {"PUBLISHED", "SOLD_OUT"})
    void unpublishedOrCancelledEventsCannotRegisterAttendance(EventStatus status) {
        Ticket ticket = ticket(22L);
        ticket.getEvent().setStatus(status);
        when(tickets.findLockedByCode(ticket.getCode())).thenReturn(Optional.of(ticket));

        assertThat(service.validateTicketForEvent(ticket.getCode(), 22L, "Puerta 1").getStatus()).isNotEqualTo("VALID");
        assertThat(ticket.isUsed()).isFalse();
        verify(tickets, never()).save(any());
    }

    @Test void aSoldOutPublishedEventStillAdmitsPreviouslyPaidTickets() {
        Ticket ticket = ticket(22L);
        ticket.getEvent().setStatus(EventStatus.SOLD_OUT);
        when(tickets.findLockedByCode(ticket.getCode())).thenReturn(Optional.of(ticket));

        assertThat(service.validateTicketForEvent(ticket.getCode(), 22L, "Puerta 3").getStatus()).isEqualTo("VALID");
        assertThat(ticket.isUsed()).isTrue();
    }

    @Test void emptyOrUnknownQrDoesNotWriteAttendance() {
        assertThat(service.consultTicketForEvent(" ", 22L).getStatus()).isEqualTo("NOT_FOUND");
        assertThat(service.validateTicketForEvent(null, 22L, "Puerta 1").getStatus()).isEqualTo("NOT_FOUND");
        when(tickets.findLockedByCode("INEXISTENTE")).thenReturn(Optional.empty());
        assertThat(service.validateTicketForEvent("INEXISTENTE", 22L, "Puerta 1").getStatus()).isEqualTo("NOT_FOUND");
        verify(tickets, never()).save(any());
    }

    @Test void databaseLookupForAdmissionDeclaresAnExclusiveWriteLock() throws Exception {
        Lock lock = TicketRepository.class.getMethod("findLockedByCode", String.class).getAnnotation(Lock.class);
        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }
}
