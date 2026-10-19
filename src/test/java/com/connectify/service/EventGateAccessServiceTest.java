package com.connectify.service;

import com.connectify.entity.Event;
import com.connectify.entity.EventGateAccess;
import com.connectify.entity.EventStatus;
import com.connectify.repository.EventGateAccessRepository;
import com.connectify.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EventGateAccessServiceTest {
    private final EventRepository events = mock(EventRepository.class);
    private final EventGateAccessRepository gates = mock(EventGateAccessRepository.class);
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(4);
    private final EventGateAccessService service = new EventGateAccessService(events, gates, passwords);
    private Event event;

    @BeforeEach void setUp() {
        event = new Event();
        ReflectionTestUtils.setField(event, "id", 22L);
        event.setStatus(EventStatus.PUBLISHED);
        event.setGateAccessCode("EVT-22");
        when(events.findLockedById(22L)).thenReturn(Optional.of(event));
        when(events.findByGateAccessCodeIgnoreCase("EVT-22")).thenReturn(Optional.of(event));
        when(events.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(gates.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(gates.findByEventIdOrderBySlotAsc(22L)).thenReturn(List.of());
    }

    private EventGateAccess gate(int slot, String password) {
        EventGateAccess gate = new EventGateAccess();
        ReflectionTestUtils.setField(gate, "id", 100L + slot);
        gate.setEvent(event);
        gate.setSlot(slot);
        gate.setLabel("Puerta " + slot);
        gate.setCredentialHash(passwords.encode(password));
        gate.setActive(true);
        gate.setAccessVersion(5L);
        return gate;
    }

    private EventGateAccess configured(int slot, String password) {
        EventGateAccess gate = gate(slot, password);
        when(gates.findByEventIdAndSlot(22L, slot)).thenReturn(Optional.of(gate));
        when(gates.findById(gate.getId())).thenReturn(Optional.of(gate));
        when(gates.findByEventIdOrderBySlotAsc(22L)).thenReturn(List.of(gate));
        return gate;
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4})
    void administratorCanConfigureEachOfTheFourDoorsAndOnlyTheHashIsStored(int slot) {
        event.setGatePassword("legacy-plaintext");
        String rawPassword = "Acceso-de-prueba-" + slot;

        EventGateAccess saved = service.saveSlot(22L, slot, "  Ingreso " + slot + "  ", rawPassword, true);

        assertThat(saved.getSlot()).isEqualTo(slot);
        assertThat(saved.getEvent()).isSameAs(event);
        assertThat(saved.getLabel()).isEqualTo("Ingreso " + slot);
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getCredentialHash()).isNotEqualTo(rawPassword);
        assertThat(passwords.matches(rawPassword, saved.getCredentialHash())).isTrue();
        assertThat(event.getGatePassword()).isNull();
        verify(events).findLockedById(22L);
        verify(gates).save(saved);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 5, 100})
    void refusesDoorNumbersOutsideOneThroughFour(int slot) {
        assertThatThrownBy(() -> service.saveSlot(22L, slot, "Puerta", "Password-test-2026", true))
                .isInstanceOf(IllegalArgumentException.class);
        verify(gates, never()).save(any());
        verify(events, never()).save(any());
    }

    @Test void listsExactlyFourSlotsAndDistinguishesUnconfiguredAndDisabledDoors() {
        EventGateAccess second = gate(2, "Password-puerta-2");
        second.setActive(false);
        EventGateAccess fourth = gate(4, "Password-puerta-4");
        when(gates.findByEventIdOrderBySlotAsc(22L)).thenReturn(List.of(second, fourth));

        var slots = service.slots(22L);

        assertThat(slots).extracting(EventGateAccessService.GateSlot::slot).containsExactly(1, 2, 3, 4);
        assertThat(slots).extracting(EventGateAccessService.GateSlot::configured).containsExactly(false, true, false, true);
        assertThat(slots.get(1).active()).isFalse();
        assertThat(slots.get(3).name()).isEqualTo("Puerta 4");
    }

    @Test void duplicateDoorPasswordIsRejectedEvenThoughBcryptHashesUseDifferentSalts() {
        String password = "Una-clave-compartida-2026";
        EventGateAccess first = gate(1, password);
        String independentHash = passwords.encode(password);
        assertThat(first.getCredentialHash()).isNotEqualTo(independentHash);
        assertThat(passwords.matches(password, independentHash)).isTrue();
        when(gates.findByEventIdOrderBySlotAsc(22L)).thenReturn(List.of(first));

        assertThatThrownBy(() -> service.saveSlot(22L, 2, "Puerta 2", password, true))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("diferente");
        verify(gates, never()).save(any());
    }

    @Test void aDisabledDoorPasswordCannotBeReusedByAnotherDoor() {
        EventGateAccess first = gate(1, "Clave-exclusiva-2026");
        first.setActive(false);
        when(gates.findByEventIdOrderBySlotAsc(22L)).thenReturn(List.of(first));

        assertThatThrownBy(() -> service.saveSlot(22L, 2, "Puerta 2", "Clave-exclusiva-2026", true))
                .isInstanceOf(IllegalArgumentException.class);
        verify(gates, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "corta", "          ", "😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀"})
    void newDoorRequiresANonblankPasswordOfSafeLength(String password) {
        assertThatThrownBy(() -> service.saveSlot(22L, 1, "Puerta 1", password, true))
                .isInstanceOf(IllegalArgumentException.class);
        verify(gates, never()).save(any());
    }

    @Test void updatingAnExistingDoorWithoutANewPasswordKeepsItsHashAndRevokesItsSession() {
        EventGateAccess first = configured(1, "Original-puerta-2026");
        String originalHash = first.getCredentialHash();
        var session = service.open("EVT-22", "Original-puerta-2026");
        assertThat(service.resolve(session)).isPresent();

        service.saveSlot(22L, 1, "Acceso principal", "", true);

        assertThat(first.getCredentialHash()).isEqualTo(originalHash);
        assertThat(first.getLabel()).isEqualTo("Acceso principal");
        assertThat(service.resolve(session)).isEmpty();
    }

    @Test void passwordRotationRejectsTheOldPasswordAndRevokesTheExistingSession() {
        EventGateAccess first = configured(1, "Original-puerta-2026");
        var session = service.open("EVT-22", "Original-puerta-2026");

        service.saveSlot(22L, 1, "Puerta 1", "Reemplazo-puerta-2026", true);

        assertThat(service.resolve(session)).isEmpty();
        assertThatThrownBy(() -> service.open("EVT-22", "Original-puerta-2026"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(service.open("EVT-22", "Reemplazo-puerta-2026").gateId()).isEqualTo(first.getId());
    }

    @Test void disablingOneDoorImmediatelyRevokesThatDoorSession() {
        configured(1, "Puerta-uno-test-2026");
        var session = service.open("EVT-22", "Puerta-uno-test-2026");

        service.saveSlot(22L, 1, "Puerta 1", "", false);

        assertThat(service.resolve(session)).isEmpty();
        assertThatThrownBy(() -> service.open("EVT-22", "Puerta-uno-test-2026"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void distinctDoorPasswordsCreateSessionsScopedToTheCorrectDoor() {
        EventGateAccess first = gate(1, "Primera-puerta-2026");
        EventGateAccess second = gate(2, "Segunda-puerta-2026");
        EventGateAccess third = gate(3, "Tercera-puerta-2026");
        EventGateAccess fourth = gate(4, "Cuarta-puerta-2026");
        when(gates.findByEventIdOrderBySlotAsc(22L)).thenReturn(List.of(first, second, third, fourth));

        assertThat(service.open(" evt-22 ", "Primera-puerta-2026").gateId()).isEqualTo(first.getId());
        assertThat(service.open("EVT-22", "Segunda-puerta-2026").gateId()).isEqualTo(second.getId());
        assertThat(service.open("EVT-22", "Tercera-puerta-2026").gateId()).isEqualTo(third.getId());
        assertThat(service.open("EVT-22", "Cuarta-puerta-2026").gateId()).isEqualTo(fourth.getId());
        assertThat(service.open("EVT-22", "Cuarta-puerta-2026").eventId()).isEqualTo(22L);
    }

    @Test void legacySharedPasswordNeverAuthenticatesADoor() {
        event.setGatePassword("Clave-antigua-2026");

        assertThatThrownBy(() -> service.open("EVT-22", "Clave-antigua-2026"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @EnumSource(value = EventStatus.class, mode = EnumSource.Mode.EXCLUDE, names = {"PUBLISHED", "SOLD_OUT"})
    void doorAccessRequiresAnEventEnabledForAttendance(EventStatus status) {
        configured(1, "Puerta-uno-test-2026");
        var session = service.open("EVT-22", "Puerta-uno-test-2026");
        event.setStatus(status);

        assertThat(service.resolve(session)).isEmpty();
        assertThatThrownBy(() -> service.open("EVT-22", "Puerta-uno-test-2026"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @EnumSource(value = EventStatus.class, names = {"PUBLISHED", "SOLD_OUT"})
    void publicationAndSoldOutStatesPermitAttendance(EventStatus status) {
        EventGateAccess first = configured(1, "Puerta-uno-test-2026");
        event.setStatus(status);
        Instant before = Instant.now();

        var session = service.open("EVT-22", "Puerta-uno-test-2026");

        assertThat(session.gateId()).isEqualTo(first.getId());
        assertThat(session.expiresAt()).isBetween(before.plus(Duration.ofHours(12)), Instant.now().plus(Duration.ofHours(12)));
        assertThat(service.resolve(session)).hasValueSatisfying(context -> {
            assertThat(context.event()).isSameAs(event);
            assertThat(context.gate()).isSameAs(first);
        });
    }

    @Test void expiredMissingAndCrossEventSessionsAreRejected() {
        EventGateAccess first = configured(1, "Puerta-uno-test-2026");
        assertThat(service.resolve(null)).isEmpty();
        assertThat(service.resolve(new EventGateAccessService.GateSession(22L, first.getId(), 5, Instant.now().minusSeconds(1)))).isEmpty();
        assertThat(service.resolve(new EventGateAccessService.GateSession(22L, first.getId(), 5, null))).isEmpty();
        assertThat(service.resolve(new EventGateAccessService.GateSession(99L, first.getId(), 5, Instant.now().plusSeconds(60)))).isEmpty();
        assertThat(service.resolve(new EventGateAccessService.GateSession(22L, 999L, 5, Instant.now().plusSeconds(60)))).isEmpty();
    }

    @Test void rejectedCodeAndPasswordUseTheSamePublicErrorWithoutRevealingEventExistence() {
        configured(1, "Puerta-uno-test-2026");
        Throwable unknownEvent = catchThrowable(() -> service.open("EVT-99", "Puerta-uno-test-2026"));
        Throwable invalidPassword = catchThrowable(() -> service.open("EVT-22", "Otra-password-2026"));
        assertThat(unknownEvent).isInstanceOf(IllegalArgumentException.class);
        assertThat(invalidPassword).isInstanceOf(IllegalArgumentException.class);
        assertThat(unknownEvent.getMessage()).isEqualTo(invalidPassword.getMessage());
    }

    @Test void ensuresAStableEventCodeWithoutUsingItsOldSharedPassword() {
        event.setGateAccessCode(null);
        assertThat(service.ensureEventCode(22L)).isEqualTo("EVT-22");
        assertThat(service.ensureEventCode(22L)).isEqualTo("EVT-22");
    }

    @Test void changingTheEventCodeRevokesAllDoorSessions() {
        EventGateAccess first = configured(1, "Puerta-uno-test-2026");
        var session = service.open("EVT-22", "Puerta-uno-test-2026");

        service.updateEventCode(22L, " festival-2026 ");

        assertThat(event.getGateAccessCode()).isEqualTo("FESTIVAL-2026");
        assertThat(first.getAccessVersion()).isEqualTo(6L);
        assertThat(service.resolve(session)).isEmpty();
    }

    @Test void anotherEventsCodeCannotBeAssigned() {
        Event other = new Event();
        ReflectionTestUtils.setField(other, "id", 99L);
        when(events.findByGateAccessCodeIgnoreCase("OCUPADO")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.updateEventCode(22L, "OCUPADO"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(event.getGateAccessCode()).isEqualTo("EVT-22");
        verify(events, never()).save(any());
    }
}
