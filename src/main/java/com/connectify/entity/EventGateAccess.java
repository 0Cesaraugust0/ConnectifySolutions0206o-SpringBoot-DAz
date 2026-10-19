package com.connectify.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "event_gate_accesses", uniqueConstraints =
        @UniqueConstraint(name = "uq_event_gate_slot", columnNames = {"event_id", "slot"}))
@org.hibernate.annotations.Check(constraints = "slot between 1 and 4")
public class EventGateAccess {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;
    @Column(nullable = false) private int slot;
    @Column(nullable = false, length = 60) private String label;
    @Column(nullable = false, length = 100) private String credentialHash;
    @Column(nullable = false) private boolean active = true;
    @Column(nullable = false) private long accessVersion = 1;
    @Column(nullable = false) private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public Event getEvent() { return event; }
    public void setEvent(Event event) { this.event = event; }
    public int getSlot() { return slot; }
    public void setSlot(int slot) { this.slot = slot; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getCredentialHash() { return credentialHash; }
    public void setCredentialHash(String credentialHash) { this.credentialHash = credentialHash; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public long getAccessVersion() { return accessVersion; }
    public void setAccessVersion(long version) { this.accessVersion = version; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant time) { updatedAt = time; }
}
