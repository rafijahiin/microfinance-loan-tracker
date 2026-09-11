package bd.org.pksf.loantracker.loan;

import bd.org.pksf.loantracker.common.Money;
import bd.org.pksf.loantracker.security.AccessGuard;
import bd.org.pksf.loantracker.security.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
public class PortfolioService {

    private final LoanRepository loans;
    private final AccessGuard guard;

    public PortfolioService(LoanRepository loans, AccessGuard guard) {
        this.loans = loans;
        this.guard = guard;
    }

    /**
     * Portfolio at Risk over 30 days.
     *
     * PAR30 is the outstanding balance of every loan carrying an instalment
     * more than 30 days late, over the total outstanding. Note that the whole
     * remaining balance of a late loan counts, not just the overdue
     * instalments: the sector's assumption is that a borrower 30 days behind
     * puts the entire balance at risk, not only the part already missed.
     * Counting just the missed instalments produces a far prettier number that
     * means something else entirely.
     *
     * Written-off loans are excluded. They have already been recognised as a
     * loss, so leaving them in would count the same money twice and, worse,
     * write-offs would improve the ratio by leaving the denominator.
     */
    public record PortfolioSummary(
            long activeLoans,
            BigDecimal outstanding,
            BigDecimal overdue,
            long loansInArrears,
            BigDecimal par30,
            LocalDate asOf) {
    }

    @Transactional(readOnly = true)
    public PortfolioSummary summary(AuthenticatedUser caller, LocalDate asOf) {
        LocalDate on = asOf == null ? LocalDate.now() : asOf;
        List<Loan> active = loans.findByStatusWithSchedule(
                LoanStatus.ACTIVE, guard.scopeOf(caller));

        BigDecimal outstanding = Money.zero();
        BigDecimal overdue = Money.zero();
        BigDecimal atRisk = Money.zero();
        long inArrears = 0;

        for (Loan loan : active) {
            BigDecimal loanOutstanding = loan.getOutstanding();
            outstanding = outstanding.add(loanOutstanding);
            overdue = overdue.add(loan.getOverdueAmount(on));

            if (loan.getDaysInArrears(on) > 0) {
                inArrears++;
            }
            if (loan.getDaysInArrears(on) > 30) {
                atRisk = atRisk.add(loanOutstanding);
            }
        }

        BigDecimal par30 = outstanding.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP)
                : atRisk.divide(outstanding, 4, RoundingMode.HALF_UP);

        return new PortfolioSummary(active.size(), Money.normalise(outstanding),
                Money.normalise(overdue), inArrears, par30, on);
    }
}
