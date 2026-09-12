package io.github.rafijahiin.loantracker.loan;

import io.github.rafijahiin.loantracker.borrower.Borrower;
import io.github.rafijahiin.loantracker.partner.PartnerOrganisation;
import io.github.rafijahiin.loantracker.security.AuthenticatedUser;
import io.github.rafijahiin.loantracker.support.IntegrationTestBase;
import io.github.rafijahiin.loantracker.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Two people changing one loan at the same time.
 *
 * This is the scenario the whole optimistic lock exists for, and it is worth
 * spelling out why it needs more than an @Version annotation. A repayment
 * usually touches only instalment rows, and in JPA modifying a child collection
 * does not make the parent row dirty. So @Version on Loan would sit at zero
 * forever and two concurrent payments would each read the same outstanding
 * balance, each decide their amount fits, and the second commit would quietly
 * overwrite the first. One receipt would remain in the cash book and vanish
 * from the balance, which is the worst kind of discrepancy because nothing
 * errors.
 */
class ConcurrentRepaymentTest extends IntegrationTestBase {

    @Autowired private RepaymentService repaymentService;
    @Autowired private LoanService loanService;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;
    private AuthenticatedUser officer;
    private Long loanId;

    private static final LocalDate DISBURSED = LocalDate.of(2026, 1, 10);

    @BeforeEach
    void setUpOneLoan() {
        tx = new TransactionTemplate(transactionManager);

        PartnerOrganisation po = partner("PO-001", "Shomota", "Rangpur");
        Borrower b = borrowers.save(new Borrower(po, "M-0001", "Rahima Begum",
                "Rangpur", DISBURSED.minusMonths(1)));
        officer = new AuthenticatedUser(1L, "officer@example.org",
                Role.PO_OFFICER, po.getId());

        // 12,000 at zero interest over 4 months: four instalments of 3,000.
        loanId = tx.execute(s -> loanService.disburse(officer, b.getId(), "L-1",
                new BigDecimal("12000"), BigDecimal.ZERO, 4,
                RepaymentFrequency.MONTHLY, DISBURSED).getId());
    }

    @Test
    @DisplayName("a repayment advances the loan's version even though only instalments changed")
    void aRepaymentForcesTheAggregateVersionForward() {
        Long before = tx.execute(s -> loanService.get(officer, loanId).getVersion());

        tx.execute(s -> repaymentService.record(officer, loanId, "R-001",
                new BigDecimal("3000"), DISBURSED.plusMonths(1)));

        Long after = tx.execute(s -> loanService.get(officer, loanId).getVersion());

        // Without the forced increment this assertion fails, and every other
        // test in the suite still passes. That is exactly how a lost update
        // ships.
        assertThat(after).isGreaterThan(before);
    }

    @Test
    @DisplayName("the second of two concurrent writers is rejected, not silently ignored")
    void aStaleWriteIsRefused() {
        // Both clerks load the loan. Each now holds a copy at version N.
        Loan clerkOne = tx.execute(s -> loanService.get(officer, loanId));
        Loan clerkTwo = tx.execute(s -> loanService.get(officer, loanId));

        assertThat(clerkOne.getVersion()).isEqualTo(clerkTwo.getVersion());

        // The first commits.
        tx.execute(s -> {
            clerkOne.setStatus(LoanStatus.WRITTEN_OFF);
            return loans.saveAndFlush(clerkOne);
        });

        // The second commits against the version it read, which has moved.
        assertThatThrownBy(() -> tx.execute(s -> {
            clerkTwo.setStatus(LoanStatus.CLOSED);
            return loans.saveAndFlush(clerkTwo);
        })).isInstanceOf(ObjectOptimisticLockingFailureException.class);

        // The first writer's change is the one that survived. The point is not
        // which one wins but that the loser is told.
        Loan settled = tx.execute(s -> loanService.get(officer, loanId));
        assertThat(settled.getStatus()).isEqualTo(LoanStatus.WRITTEN_OFF);
    }

    @Test
    @DisplayName("sequential repayments are unaffected, each seeing the previous one")
    void theLockDoesNotGetInTheWayOfNormalWork() {
        // The lock must only bite on genuine concurrency. If it rejected
        // ordinary back-to-back collection it would be worse than useless.
        for (int i = 1; i <= 4; i++) {
            final String receipt = String.format("R-%03d", i);
            final LocalDate on = DISBURSED.plusMonths(i);
            tx.execute(s -> repaymentService.record(officer, loanId, receipt,
                    new BigDecimal("3000"), on));
        }

        Loan loan = tx.execute(s -> loanService.get(officer, loanId));
        assertThat(loan.getTotalPaid()).isEqualByComparingTo("12000.00");
        assertThat(loan.getOutstanding()).isEqualByComparingTo("0.00");
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.CLOSED);
    }
}
