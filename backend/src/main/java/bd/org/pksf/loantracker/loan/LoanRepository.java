package bd.org.pksf.loantracker.loan;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface LoanRepository extends JpaRepository<Loan, Long> {

    boolean existsByLoanNumber(String loanNumber);

    /** Fetches the schedule in one query. Every derived figure on Loan walks
     *  the instalment list, so loading them lazily turns one loan lookup into
     *  a second query, and a page of twenty loans into twenty-one. */
    @Query("""
           select distinct l from Loan l
           join fetch l.borrower b
           join fetch b.partner
           left join fetch l.instalments
           where l.id = :id
           """)
    Optional<Loan> findByIdWithSchedule(Long id);

    /**
     * Paginates IDs only, then the schedule is fetched for that page in a
     * second query (see findAllWithScheduleByIds).
     *
     * Doing it in one query with `left join fetch l.instalments` and a Pageable
     * is the trap: the join multiplies rows by instalment, so LIMIT would cut
     * the result mid-loan. Hibernate avoids returning wrong data by pulling the
     * ENTIRE result set into memory and paginating there, warning HHH000104 as
     * it goes. That works on six seed loans and falls over on a real portfolio.
     *
     * Two queries, each correct, is the standard answer.
     */
    @Query("""
           select l.id from Loan l
           where (:partnerId is null or l.borrower.partner.id = :partnerId)
             and (:status is null or l.status = :status)
           """)
    Page<Long> searchIds(Long partnerId, LoanStatus status, Pageable pageable);

    @Query("""
           select distinct l from Loan l
           join fetch l.borrower b
           join fetch b.partner
           left join fetch l.instalments
           where l.id in :ids
           """)
    List<Loan> findAllWithScheduleByIds(List<Long> ids);

    @Query("""
           select distinct l from Loan l
           left join fetch l.instalments
           where l.status = :status
             and (:partnerId is null or l.borrower.partner.id = :partnerId)
           """)
    List<Loan> findByStatusWithSchedule(LoanStatus status, Long partnerId);

    @Query("""
           select distinct l from Loan l
           join fetch l.borrower b
           join fetch b.partner
           left join fetch l.instalments
           where b.id = :borrowerId
           """)
    List<Loan> findByBorrowerId(Long borrowerId);
}
