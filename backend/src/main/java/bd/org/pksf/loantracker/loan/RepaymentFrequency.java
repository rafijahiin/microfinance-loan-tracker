package bd.org.pksf.loantracker.loan;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * How often an instalment falls due.
 *
 * Weekly is the norm for group lending here: collection happens at the weekly
 * samity meeting, which is what makes repayment social rather than
 * administrative. Monthly suits larger individual and enterprise loans. A
 * tracker that only understood one of them would be unusable for half the
 * portfolio, so the frequency belongs on the loan rather than in the schedule
 * generator's assumptions.
 */
public enum RepaymentFrequency {

    WEEKLY(52, ChronoUnit.WEEKS),
    MONTHLY(12, ChronoUnit.MONTHS);

    private final int periodsPerYear;
    private final ChronoUnit unit;

    RepaymentFrequency(int periodsPerYear, ChronoUnit unit) {
        this.periodsPerYear = periodsPerYear;
        this.unit = unit;
    }

    public int getPeriodsPerYear() {
        return periodsPerYear;
    }

    /** The due date of instalment `n`, counted from disbursement.
     *
     *  For MONTHLY this clamps a month-end date to the shortest month, so a
     *  loan disbursed on the 31st falls due on the 28th rather than rolling
     *  into the following month. */
    public LocalDate dueDate(LocalDate disbursedOn, int instalmentNo) {
        return disbursedOn.plus(instalmentNo, unit);
    }
}
