package io.github.rafijahiin.loantracker.audit;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One thing that happened, who did it, and when.
 *
 * Append-only by design. There is no setter, no update path and no delete
 * endpoint: a trail that can be edited answers nothing, because the first
 * question about any entry would be whether it is the original. The repository
 * exposes reads and a save, and nothing else.
 *
 * Scoped like every other read in this system. An audit trail that ignored the
 * partner boundary would be the one place an officer could learn about another
 * organisation's lending, which is a strange hole to leave in a system that
 * closes it everywhere else.
 *
 * Note what is NOT recorded: no national ID, no password, no token. The
 * enrolment entry carries the masked number that staff already see on screen.
 * An audit table is a long-lived, widely-read copy of whatever you put in it,
 * so it is the last place secrets should end up.
 */
@Entity
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AuditAction action;

    /** Who. Stored as the email rather than a foreign key to app_user, because
     *  the trail has to outlive the account: a clerk who leaves still posted
     *  the receipts they posted, and a deleted user must not blank the record
     *  of what they did. */
    @Column(name = "actor_email", nullable = false, length = 255)
    private String actorEmail;

    @Column(name = "actor_role", nullable = false, length = 20)
    private String actorRole;

    /** Which partner's data this concerned. Drives scoping on read. */
    @Column(name = "partner_id")
    private Long partnerId;

    /** What it happened to, so a loan's history can be pulled without parsing
     *  prose. */
    @Column(name = "entity_type", nullable = false, length = 30)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    /** A line a person can read without knowing the schema. */
    @Column(nullable = false, length = 500)
    private String summary;

    /** The money involved, where there was any, so entries can be totalled
     *  without re-parsing the summary. */
    @Column(precision = 15, scale = 2)
    private BigDecimal amount;

    protected AuditEvent() {
    }

    AuditEvent(AuditAction action, String actorEmail, String actorRole,
               Long partnerId, String entityType, Long entityId,
               String summary, BigDecimal amount) {
        this.occurredAt = Instant.now();
        this.action = action;
        this.actorEmail = actorEmail;
        this.actorRole = actorRole;
        this.partnerId = partnerId;
        this.entityType = entityType;
        this.entityId = entityId;
        this.summary = summary;
        this.amount = amount;
    }

    public Long getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public AuditAction getAction() {
        return action;
    }

    public String getActorEmail() {
        return actorEmail;
    }

    public String getActorRole() {
        return actorRole;
    }

    public Long getPartnerId() {
        return partnerId;
    }

    public String getEntityType() {
        return entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public String getSummary() {
        return summary;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
