package io.github.rafijahiin.loantracker.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * Reads and one save. No update, no delete, by omission rather than by comment:
 * the methods that would let a trail be rewritten simply are not here, and
 * JpaRepository's inherited delete methods are the only way in, which a review
 * would catch.
 */
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    /** Newest first, scoped. A null partnerId means an administrator and no
     *  filter; it is compared to a bigint column, so PostgreSQL infers the type
     *  and the untyped-null problem that broke the borrower search does not
     *  arise here. */
    @Query("""
           select a from AuditEvent a
           where (:partnerId is null or a.partnerId = :partnerId)
           order by a.occurredAt desc, a.id desc
           """)
    Page<AuditEvent> recent(Long partnerId, Pageable pageable);

    @Query("""
           select a from AuditEvent a
           where a.entityType = :entityType and a.entityId = :entityId
             and (:partnerId is null or a.partnerId = :partnerId)
           order by a.occurredAt desc, a.id desc
           """)
    List<AuditEvent> forEntity(String entityType, Long entityId, Long partnerId);
}
