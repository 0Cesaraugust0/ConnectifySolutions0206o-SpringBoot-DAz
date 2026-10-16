package com.connectify.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "account_access_audits")
public class AccountAccessAudit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private String actorEmail;
    @Column(nullable = false) private String targetEmail;
    @Column(nullable = false) private String action;
    @Column(nullable = false, length = 1000) private String detail;
    @Column(nullable = false) private Instant createdAt = Instant.now();

    protected AccountAccessAudit() { }
    public AccountAccessAudit(String actorEmail, String targetEmail, String action, String detail) {
        this.actorEmail = actorEmail; this.targetEmail = targetEmail;
        this.action = action; this.detail = detail;
    }
    public Long getId() { return id; }
    public String getActorEmail() { return actorEmail; }
    public String getTargetEmail() { return targetEmail; }
    public String getAction() { return action; }
    public String getDetail() { return detail; }
    public Instant getCreatedAt() { return createdAt; }
}
