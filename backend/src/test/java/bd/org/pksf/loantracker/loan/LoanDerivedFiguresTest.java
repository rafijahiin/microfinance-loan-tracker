package bd.org.pksf.loantracker.loan;

import bd.org.pksf.loantracker.borrower.Borrower;
import bd.org.pksf.loantracker.partner.PartnerOrganisation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** The balances a branch argues about, computed from the schedule rather than
 *  stored, so they cannot drift away from the rows that back them. */
class LoanDerivedFiguresTest {

    private static final LocalDate DISBURSED = LocalDate.of(2026, 1, 10);

    private Loan loan;

    @BeforeEach
    void setUp() {
        PartnerOrganisation po = new PartnerOrganisation("PO-001", "Test PO", "Rangpur");
        Borrower b = new Borrower(po, "M-0001", "Test Member", "Rangpur", DISBURSED);

        // 12,000 at zero interest over 4 months: four instalments of 3,000
        // falling due 10 Feb, 10 Mar, 10 Apr and 10 May.
        loan = new Loan("L-1", b, new BigDecimal("12000"), BigDecimal.ZERO, 4,
                RepaymentFrequency.MONTHLY, DISBURSED);
        new ScheduleGenerator()
                .generate(new BigDecimal("12000"), BigDecimal.ZERO, 4,
                        RepaymentFrequency.MONTHLY, DISBURSED)
                .forEach(loan::addInstalment);
    }

    @Test
    void anUntouchedLoanOwesItsWholeSchedule() {
        assertThat(loan.getTotalDue()).isEqualByComparingTo("12000.00");
        assertThat(loan.getTotalPaid()).isEqualByComparingTo("0.00");
        assertThat(loan.getOutstanding()).isEqualByComparingTo("12000.00");
    }

    @Test
    @DisplayName("overdue counts only what is past due, outstanding counts everything")
    void overdueAndOutstandingAreDifferentQuestions() {
        // As at 1 April: February and March have fallen due, April and May have
        // not. Nothing has been paid.
        LocalDate asOf = LocalDate.of(2026, 4, 1);

        assertThat(loan.getOverdueAmount(asOf)).isEqualByComparingTo("6000.00");
        assertThat(loan.getOutstanding()).isEqualByComparingTo("12000.00");
    }

    @Test
    @DisplayName("arrears age is measured from the oldest unpaid instalment")
    void arrearsAgeUsesTheOldestUnpaidInstalment() {
        // Using the newest would restart the clock every month and make a loan
        // that has been unpaid since February look thirty days late forever.
        LocalDate asOf = LocalDate.of(2026, 4, 1);

        assertThat(loan.getDaysInArrears(asOf))
                .isEqualTo(java.time.temporal.ChronoUnit.DAYS.between(
                        LocalDate.of(2026, 2, 10), asOf));
    }

    @Test
    void settlingTheOldestInstalmentMovesTheArrearsClockForward() {
        loan.getInstalments().get(0).apply(new BigDecimal("3000"),
                LocalDate.of(2026, 2, 10));
        LocalDate asOf = LocalDate.of(2026, 4, 1);

        assertThat(loan.getOverdueAmount(asOf)).isEqualByComparingTo("3000.00");
        assertThat(loan.getDaysInArrears(asOf))
                .isEqualTo(java.time.temporal.ChronoUnit.DAYS.between(
                        LocalDate.of(2026, 3, 10), asOf));
    }

    @Test
    void aFullySettledLoanHasNoArrearsAtAnyDate() {
        loan.getInstalments().forEach(i ->
                i.apply(i.getAmountDue(), i.getDueOn()));

        assertThat(loan.getOutstanding()).isEqualByComparingTo("0.00");
        assertThat(loan.getDaysInArrears(LocalDate.of(2027, 1, 1))).isZero();
        assertThat(loan.getOverdueAmount(LocalDate.of(2027, 1, 1)))
                .isEqualByComparingTo("0.00");
    }
}
