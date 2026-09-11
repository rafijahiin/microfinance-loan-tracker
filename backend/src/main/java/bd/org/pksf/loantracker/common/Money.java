package bd.org.pksf.loantracker.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Money helpers.
 *
 *  Amounts are BigDecimal at 2 decimal places, never double. A double cannot
 *  represent 0.10 exactly, so a schedule built by repeated addition drifts and
 *  the final instalment ends a few poisha out. On a portfolio of thousands of
 *  loans that difference is what makes a reconciliation fail.
 */
public final class Money {

    public static final int SCALE = 2;

    private Money() {
    }

    public static BigDecimal of(String v) {
        return new BigDecimal(v).setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** Round to paisa, half up. Applied at every boundary so two paths to the
     *  same figure cannot disagree in the last place. */
    public static BigDecimal normalise(BigDecimal v) {
        return v == null ? null : v.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static boolean isPositive(BigDecimal v) {
        return v != null && v.compareTo(BigDecimal.ZERO) > 0;
    }
}
