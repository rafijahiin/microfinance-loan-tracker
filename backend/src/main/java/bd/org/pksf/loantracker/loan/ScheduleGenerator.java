package bd.org.pksf.loantracker.loan;

import bd.org.pksf.loantracker.common.BusinessRuleException;
import bd.org.pksf.loantracker.common.Money;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a flat-rate repayment schedule, weekly or monthly.
 *
 * Flat rate, not declining balance: interest is charged on the original
 * principal for the whole term, so every instalment carries the same interest.
 * This is how the service charge on a group loan is quoted in this sector, and
 * quoting a declining-balance figure against a flat-rate product would make
 * every instalment in this file wrong.
 *
 *   total interest = principal x annual rate x term in years
 *   instalment     = (principal + total interest) / number of instalments
 *
 * The rounding rule is the part worth reading. Dividing by the term almost
 * never comes out exact, so each of the first n-1 instalments takes the amount
 * rounded DOWN and the final one takes whatever is left. The alternative,
 * rounding every instalment and letting the total fall where it may, produces a
 * schedule whose instalments do not add up to what the borrower was lent. That
 * difference is small per loan and impossible to reconcile across a portfolio.
 */
@Component
public class ScheduleGenerator {

    public List<Instalment> generate(BigDecimal principal, BigDecimal annualRate,
                                     int termPeriods, RepaymentFrequency frequency,
                                     LocalDate disbursedOn) {
        if (!Money.isPositive(principal)) {
            throw new BusinessRuleException("Principal must be greater than zero");
        }
        if (annualRate == null || annualRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException("Annual rate cannot be negative");
        }
        if (termPeriods < 1) {
            throw new BusinessRuleException("Term must be at least one instalment");
        }
        if (frequency == null) {
            throw new BusinessRuleException("A repayment frequency is required");
        }

        BigDecimal p = Money.normalise(principal);
        BigDecimal n = BigDecimal.valueOf(termPeriods);

        // Term in YEARS, derived from the frequency. Flat interest is priced
        // per year, so 40 weekly instalments must become 40/52 of a year and
        // not 40/12. Getting this wrong overstates the service charge on every
        // weekly loan by more than four times.
        BigDecimal years = n.divide(
                BigDecimal.valueOf(frequency.getPeriodsPerYear()), 10,
                RoundingMode.HALF_UP);
        BigDecimal totalInterest = Money.normalise(p.multiply(annualRate).multiply(years));

        BigDecimal basePrincipal = p.divide(n, Money.SCALE, RoundingMode.DOWN);
        BigDecimal baseInterest = totalInterest.divide(n, Money.SCALE, RoundingMode.DOWN);

        BigDecimal allocatedPrincipal = basePrincipal.multiply(BigDecimal.valueOf(termPeriods - 1L));
        BigDecimal allocatedInterest = baseInterest.multiply(BigDecimal.valueOf(termPeriods - 1L));

        List<Instalment> schedule = new ArrayList<>(termPeriods);
        for (int i = 1; i <= termPeriods; i++) {
            boolean last = (i == termPeriods);
            BigDecimal principalDue = last
                    ? Money.normalise(p.subtract(allocatedPrincipal))
                    : basePrincipal;
            BigDecimal interestDue = last
                    ? Money.normalise(totalInterest.subtract(allocatedInterest))
                    : baseInterest;

            schedule.add(new Instalment(i, frequency.dueDate(disbursedOn, i),
                    principalDue, interestDue));
        }
        return schedule;
    }
}
