package com.connectify.service;

import com.connectify.entity.Event;
import com.connectify.entity.EventGateAccess;
import com.connectify.entity.EventStatus;
import com.connectify.repository.EventGateAccessRepository;
import com.connectify.repository.EventRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class EventGateAccessService {
    public static final String SESSION_KEY = "CONNECTIFY_EVENT_GATE";
    private static final String INVALID_ACCESS = "Código o contraseña incorrectos, o acceso no habilitado para este evento.";
    private final EventRepository events;
    private final EventGateAccessRepository gates;
    private final PasswordEncoder passwords;

    public EventGateAccessService(EventRepository events, EventGateAccessRepository gates, PasswordEncoder passwords) {
        this.events = events;
        this.gates = gates;
        this.passwords = passwords;
    }

    @Transactional(readOnly = true)
    public List<GateSlot> slots(Long eventId) {
        List<EventGateAccess> configured = gates.findByEventIdOrderBySlotAsc(eventId);
        List<GateSlot> result = new ArrayList<>();
        for (int slot = 1; slot <= 4; slot++) {
            int number = slot;
            EventGateAccess gate = configured.stream().filter(g -> g.getSlot() == number).findFirst().orElse(null);
            result.add(new GateSlot(slot, gate == null ? "Puerta " + slot : gate.getLabel(), gate != null,
                    gate == null || gate.isActive()));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Map<Long, Long> activeCounts() {
        return gates.countActiveByEvent().stream().collect(Collectors.toMap(
                EventGateAccessRepository.GateCount::getEventId, EventGateAccessRepository.GateCount::getTotal));
    }

    @Transactional
    public EventGateAccess saveSlot(Long eventId, int slot, String label, String newPassword, boolean active) {
        if (slot < 1 || slot > 4) throw new IllegalArgumentException("Puedes configurar de 1 a 4 puertas por evento.");
        String name = label == null ? "" : label.trim();
        if (name.isBlank() || name.length() > 60) throw new IllegalArgumentException("Escribe un nombre de puerta de hasta 60 caracteres.");
        Event event = lockedEvent(eventId);
        EventGateAccess gate = gates.findByEventIdAndSlot(eventId, slot).orElseGet(EventGateAccess::new);
        String password = newPassword == null ? "" : newPassword;
        if (gate.getId() == null && password.isEmpty()) throw new IllegalArgumentException("Asigna una contraseña para habilitar esta puerta.");
        if (!password.isEmpty()) {
            if (password.length() < 10 || password.getBytes(StandardCharsets.UTF_8).length > 72 || password.isBlank())
                throw new IllegalArgumentException("La contraseña debe tener al menos 10 caracteres y no superar 72 bytes.");
            for (EventGateAccess other : gates.findByEventIdOrderBySlotAsc(eventId)) {
                if (other.getSlot() != slot && passwords.matches(password, other.getCredentialHash()))
                    throw new IllegalArgumentException("Cada puerta del evento debe tener una contraseña diferente.");
            }
            gate.setCredentialHash(passwords.encode(password));
        }
        ensureCode(event);
        event.setGatePassword(null); // Legacy shared password is never an authentication fallback.
        events.save(event);
        gate.setEvent(event);
        gate.setSlot(slot);
        gate.setLabel(name);
        gate.setActive(active);
        gate.setAccessVersion(gate.getAccessVersion() + 1);
        gate.setUpdatedAt(Instant.now());
        return gates.save(gate);
    }

    @Transactional
    public String ensureEventCode(Long eventId) {
        Event event = lockedEvent(eventId);
        ensureCode(event);
        events.save(event);
        return event.getGateAccessCode();
    }

    @Transactional
    public Event updateEventCode(Long eventId, String rawCode) {
        Event event = lockedEvent(eventId);
        String code = normalizeCode(rawCode);
        if (!code.matches("[A-Z0-9-]{3,40}"))
            throw new IllegalArgumentException("Usa de 3 a 40 letras, números o guiones para el código del evento.");
        ensureUniqueCode(event, code);
        if (!code.equals(event.getGateAccessCode())) {
            event.setGateAccessCode(code);
            for (EventGateAccess gate : gates.findByEventIdOrderBySlotAsc(eventId)) {
                gate.setAccessVersion(gate.getAccessVersion() + 1);
                gates.save(gate);
            }
        }
        return events.save(event);
    }

    @Transactional(readOnly = true)
    public GateSession open(String rawCode, String password) {
        String code = normalizeCode(rawCode);
        if (!code.matches("[A-Z0-9-]{3,40}") || password == null || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new IllegalArgumentException(INVALID_ACCESS);
        Event event = events.findByGateAccessCodeIgnoreCase(code).orElseThrow(() -> new IllegalArgumentException(INVALID_ACCESS));
        if (!allowsEntry(event)) throw new IllegalArgumentException(INVALID_ACCESS);
        for (EventGateAccess gate : gates.findByEventIdOrderBySlotAsc(event.getId())) {
            if (gate.isActive() && gate.getSlot() >= 1 && gate.getSlot() <= 4 && passwords.matches(password, gate.getCredentialHash()))
                return new GateSession(event.getId(), gate.getId(), gate.getAccessVersion(), Instant.now().plus(Duration.ofHours(12)));
        }
        throw new IllegalArgumentException(INVALID_ACCESS);
    }

    @Transactional(readOnly = true)
    public Optional<GateContext> resolve(GateSession session) {
        if (session == null || session.gateId() == null || session.eventId() == null || session.expiresAt() == null
                || !session.expiresAt().isAfter(Instant.now())) return Optional.empty();
        return gates.findById(session.gateId()).filter(gate -> gate.isActive()
                && gate.getAccessVersion() == session.accessVersion()
                && gate.getEvent() != null && gate.getEvent().getId().equals(session.eventId())
                && allowsEntry(gate.getEvent())).map(gate -> new GateContext(gate.getEvent(), gate));
    }

    public static boolean allowsEntry(Event event) {
        return event != null && (event.getStatus() == EventStatus.PUBLISHED || event.getStatus() == EventStatus.SOLD_OUT);
    }

    private Event lockedEvent(Long id) {
        return events.findLockedById(id).orElseThrow(() -> new IllegalArgumentException("Evento no encontrado."));
    }

    private void ensureCode(Event event) {
        if (event.getGateAccessCode() == null || event.getGateAccessCode().isBlank()) {
            String code = "EVT-" + event.getId();
            ensureUniqueCode(event, code);
            event.setGateAccessCode(code);
        }
    }

    private void ensureUniqueCode(Event event, String code) {
        if (events.findByGateAccessCodeIgnoreCase(code).filter(other -> !other.getId().equals(event.getId())).isPresent())
            throw new IllegalArgumentException("Ese código ya pertenece a otro evento. Elige uno diferente.");
    }

    private String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase(java.util.Locale.ROOT);
    }

    public record GateSlot(int slot, String name, boolean configured, boolean active) { }
    public record GateSession(Long eventId, Long gateId, long accessVersion, Instant expiresAt) implements Serializable { }
    public record GateContext(Event event, EventGateAccess gate) { }
}
