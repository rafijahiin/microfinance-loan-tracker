package io.github.rafijahiin.loantracker.audit;

import io.github.rafijahiin.loantracker.security.AccessGuard;
import io.github.rafijahiin.loantracker.security.AuthenticatedUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class AuditService {

    public static final String ENTITY_LOAN = "LOAN";
    public static final String ENTITY_BORROWER = "BORROWER";

    private final AuditEventRepository events;
    private final AccessGuard guard;

    public AuditService(AuditEventRepository events, AccessGuard guard) {
        this.events = events;
        this.guard = guard;
    }

    /**
     * Writes the entry.
     *
     * Deliberately NOT in its own transaction. It joins whatever transaction
     * the change is happening in, so if that change rolls back the entry goes
     * with it. An audit trail asserting a repayment that never landed is worse
     * than no trail: it would be believed.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void record(AuthenticatedUser actor, AuditAction action, Long partnerId,
                       String entityType, Long entityId, String summary,
                       BigDecimal amount) {
        events.save(new AuditEvent(action, actor.email(), actor.role().name(),
                partnerId, entityType, entityId, summary, amount));
    }

    @Transactional(readOnly = true)
    public Page<AuditEvent> recent(AuthenticatedUser caller, Pageable pageable) {
        return events.recent(guard.scopeOf(caller), pageable);
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> forLoan(AuthenticatedUser caller, Long loanId) {
        return events.forEntity(ENTITY_LOAN, loanId, guard.scopeOf(caller));
    }
}
