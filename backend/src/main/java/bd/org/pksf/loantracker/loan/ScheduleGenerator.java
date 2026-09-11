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
 * Builds a flat-rate repayment schedule.
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

    private static final BigDecimal MONTHS_PER_YEAR = new BigDecimal("12");

    public List<Instalment> generate(BigDecimal principal, BigDecimal annualRate,
                                     int termMonths, LocalDate disbursedOn) {
        if (!Money.isPositive(principal)) {
            throw new BusinessRuleException("Principal must be greater than zero");
        }
        if (annualRate == null || annualRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException("Annual rate cannot be negative");
        }
        if (termMonths < 1) {
            throw new BusinessRuleException("Term must be at least one month");
        }

        BigDecimal p = Money.normalise(principal);
        BigDecimal n = BigDecimal.valueOf(termMonths);

        BigDecimal years = n.divide(MONTHS_PER_YEAR, 10, RoundingMode.HALF_UP);
        BigDecimal totalInterest = Money.normalise(p.multiply(annualRate).multiply(years));

        BigDecimal basePrincipal = p.divide(n, Money.SCALE, RoundingMode.DOWN);
        BigDecimal baseInterest = totalInterest.divide(n, Money.SCALE, RoundingMode.DOWN);

        BigDecimal allocatedPrincipal = basePrincipal.multiply(BigDecimal.valueOf(termMonths - 1L));
        BigDecimal allocatedInterest = baseInterest.multiply(BigDecimal.valueOf(termMonths - 1L));

        List<Instalment> schedule = new ArrayList<>(termMonths);
        for (int i = 1; i <= termMonths; i++) {
            boolean last = (i == termMonths);
            BigDecimal principalDue = last
                    ? Money.normalise(p.subtract(allocatedPrincipal))
                    : basePrincipal;
            BigDecimal interestDue = last
                    ? Money.normalise(totalInterest.subtract(allocatedInterest))
                    : baseInterest;

            // plusMonths clamps a month-end date to the shortest month, so a
            // loan disbursed on the 31st falls due on the 28th or 30th rather
            // than rolling into the following month.
            schedule.add(new Instalment(i, disbursedOn.plusMonths(i),
                    principalDue, interestDue));
        }
        return schedule;
    }
}
