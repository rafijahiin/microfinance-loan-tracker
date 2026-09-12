package io.github.rafijahiin.loantracker.audit;

import java.math.BigDecimal;
import java.time.Instant;

public record AuditEventDto(
        Long id,
        Instant occurredAt,
        String action,
        String actionLabel,
        String actorEmail,
        String actorRole,
        String entityType,
        Long entityId,
        String summary,
        BigDecimal amount) {

    public static AuditEventDto from(AuditEvent e) {
        return new AuditEventDto(
                e.getId(), e.getOccurredAt(), e.getAction().name(),
                e.getAction().getLabel(), e.getActorEmail(), e.getActorRole(),
                e.getEntityType(), e.getEntityId(), e.getSummary(), e.getAmount());
    }
}
