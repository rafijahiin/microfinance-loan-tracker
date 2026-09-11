package bd.org.pksf.loantracker.loan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class InstalmentTest {

    private static final LocalDate DUE = LocalDate.of(2026, 3, 10);
    private static final LocalDate PAID_ON = LocalDate.of(2026, 3, 9);

    private Instalment instalment() {
        return new Instalment(1, DUE, new BigDecimal("1000"), new BigDecimal("120"));
    }

    @Test
    void amountDueIsPrincipalPlusInterest() {
        assertThat(instalment().getAmountDue()).isEqualByComparingTo("1120.00");
    }

    @Test
    @DisplayName("a part payment leaves the row PARTIAL and unsettled")
    void partPaymentIsTrackedWithoutSettling() {
        Instalment i = instalment();
        BigDecimal taken = i.apply(new BigDecimal("500"), PAID_ON);

        assertThat(taken).isEqualByComparingTo("500.00");
        assertThat(i.getBalance()).isEqualByComparingTo("620.00");
        assertThat(i.getStatus()).isEqualTo(InstalmentStatus.PARTIAL);
        assertThat(i.getSettledOn()).isNull();
        assertThat(i.isSettled()).isFalse();
    }

    @Test
    void payingTheBalanceSettlesTheRowAndStampsTheDate() {
        Instalment i = instalment();
        i.apply(new BigDecimal("1120"), PAID_ON);

        assertThat(i.getBalance()).isEqualByComparingTo("0.00");
        assertThat(i.getStatus()).isEqualTo(InstalmentStatus.PAID);
        assertThat(i.getSettledOn()).isEqualTo(PAID_ON);
    }

    @Test
    @DisplayName("an instalment takes only what it is owed and leaves the rest")
    void surplusIsLeftForTheNextInstalment() {
        // This is what lets the allocation loop carry a lump sum across rows
        // without any row ever recording more than it was due.
        Instalment i = instalment();
        BigDecimal taken = i.apply(new BigDecimal("5000"), PAID_ON);

        assertThat(taken).isEqualByComparingTo("1120.00");
        assertThat(i.getAmountPaid()).isEqualByComparingTo("1120.00");
        assertThat(i.getBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void aSettledInstalmentTakesNothingFurther() {
        Instalment i = instalment();
        i.apply(new BigDecimal("1120"), PAID_ON);

        assertThat(i.apply(new BigDecimal("100"), PAID_ON)).isEqualByComparingTo("0.00");
        assertThat(i.getAmountPaid()).isEqualByComparingTo("1120.00");
    }

    @Test
    @DisplayName("overdue means past due AND unsettled, and the due date itself is not late")
    void overdueBoundaries() {
        Instalment unpaid = instalment();
        assertThat(unpaid.isOverdue(DUE)).isFalse();
        assertThat(unpaid.isOverdue(DUE.minusDays(1))).isFalse();
        assertThat(unpaid.isOverdue(DUE.plusDays(1))).isTrue();

        Instalment settled = instalment();
        settled.apply(new BigDecimal("1120"), PAID_ON);
        assertThat(settled.isOverdue(DUE.plusMonths(6))).isFalse();
    }
}
