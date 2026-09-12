package io.github.rafijahiin.loantracker.loan;

import io.github.rafijahiin.loantracker.common.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static io.github.rafijahiin.loantracker.loan.RepaymentFrequency.MONTHLY;
import static io.github.rafijahiin.loantracker.loan.RepaymentFrequency.WEEKLY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScheduleGeneratorTest {

    private final ScheduleGenerator generator = new ScheduleGenerator();

    private static final LocalDate JAN_15 = LocalDate.of(2026, 1, 15);

    // ── Monthly ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("flat interest is principal x rate x years, split evenly")
    void flatInterestIsChargedOnTheOriginalPrincipal() {
        // 30,000 at 12 per cent flat over 12 months is 3,600 of interest, so
        // 33,600 payable in 12 instalments of 2,800.
        List<Instalment> schedule = generator.generate(
                new BigDecimal("30000"), new BigDecimal("0.12"), 12, MONTHLY, JAN_15);

        assertThat(schedule).hasSize(12);
        assertThat(total(schedule)).isEqualByComparingTo("33600.00");
        assertThat(schedule).allSatisfy(i ->
                assertThat(i.getAmountDue()).isEqualByComparingTo("2800.00"));
    }

    @Test
    @DisplayName("instalments always add back up to exactly what was lent")
    void theScheduleReconcilesEvenWhenTheDivisionIsNotExact() {
        // 10,000 over 3 months does not divide cleanly. Rounding each
        // instalment independently would give 3,333.33 x 3 = 9,999.99 and lose
        // a paisa on every such loan.
        List<Instalment> schedule = generator.generate(
                new BigDecimal("10000"), BigDecimal.ZERO, 3, MONTHLY, JAN_15);

        assertThat(totalPrincipal(schedule)).isEqualByComparingTo("10000.00");
        assertThat(total(schedule)).isEqualByComparingTo("10000.00");
    }

    @Test
    @DisplayName("the final instalment absorbs the rounding remainder")
    void theRemainderLandsOnTheLastInstalment() {
        List<Instalment> schedule = generator.generate(
                new BigDecimal("10000"), BigDecimal.ZERO, 3, MONTHLY, JAN_15);

        assertThat(schedule.get(0).getAmountDue()).isEqualByComparingTo("3333.33");
        assertThat(schedule.get(1).getAmountDue()).isEqualByComparingTo("3333.33");
        assertThat(schedule.get(2).getAmountDue()).isEqualByComparingTo("3333.34");
    }

    @Test
    @DisplayName("principal and interest on each row sum to the row total")
    void componentsAgreeWithTheInstalmentAmount() {
        List<Instalment> schedule = generator.generate(
                new BigDecimal("47350"), new BigDecimal("0.1375"), 7, MONTHLY, JAN_15);

        assertThat(schedule).allSatisfy(i -> assertThat(i.getAmountDue())
                .isEqualByComparingTo(i.getPrincipalDue().add(i.getInterestDue())));
    }

    @Test
    @DisplayName("monthly instalments fall due on the same day each month")
    void monthlyDueDatesAreMonthlyFromDisbursement() {
        List<Instalment> schedule = generator.generate(
                new BigDecimal("12000"), BigDecimal.ZERO, 3, MONTHLY, JAN_15);

        assertThat(schedule.get(0).getDueOn()).isEqualTo(LocalDate.of(2026, 2, 15));
        assertThat(schedule.get(1).getDueOn()).isEqualTo(LocalDate.of(2026, 3, 15));
        assertThat(schedule.get(2).getDueOn()).isEqualTo(LocalDate.of(2026, 4, 15));
    }

    @Test
    @DisplayName("a month-end disbursement clamps to the shortest month")
    void monthEndDatesDoNotRollIntoTheFollowingMonth() {
        // Disbursed 31 January. The February instalment must fall on the 28th,
        // not spill over to 3 March.
        List<Instalment> schedule = generator.generate(
                new BigDecimal("9000"), BigDecimal.ZERO, 3, MONTHLY,
                LocalDate.of(2026, 1, 31));

        assertThat(schedule.get(0).getDueOn()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(schedule.get(1).getDueOn()).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(schedule.get(2).getDueOn()).isEqualTo(LocalDate.of(2026, 4, 30));
    }

    // ── Weekly ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("weekly instalments fall due every seven days")
    void weeklyDueDatesStepBySevenDays() {
        List<Instalment> schedule = generator.generate(
                new BigDecimal("10000"), BigDecimal.ZERO, 4, WEEKLY, JAN_15);

        assertThat(schedule.get(0).getDueOn()).isEqualTo(LocalDate.of(2026, 1, 22));
        assertThat(schedule.get(1).getDueOn()).isEqualTo(LocalDate.of(2026, 1, 29));
        assertThat(schedule.get(2).getDueOn()).isEqualTo(LocalDate.of(2026, 2, 5));
        assertThat(schedule.get(3).getDueOn()).isEqualTo(LocalDate.of(2026, 2, 12));
    }

    @Test
    @DisplayName("a weekly term is priced over 52 weeks, not 12 months")
    void weeklyInterestUsesWeeksPerYearNotMonthsPerYear() {
        // 30,000 at 12 per cent flat over 40 WEEKS is 40/52 of a year:
        // 30,000 x 0.12 x (40/52) = 2,769.23.
        //
        // Pricing that same term as if 40 were months would charge
        // 30,000 x 0.12 x (40/12) = 12,000, more than four times too much.
        // This is the single most damaging thing that can go wrong when a
        // monthly product gains a weekly sibling, so it is pinned to a figure
        // worked out by hand rather than to whatever the code happens to do.
        List<Instalment> schedule = generator.generate(
                new BigDecimal("30000"), new BigDecimal("0.12"), 40, WEEKLY, JAN_15);

        assertThat(schedule).hasSize(40);
        assertThat(totalInterest(schedule)).isEqualByComparingTo("2769.23");
        assertThat(totalPrincipal(schedule)).isEqualByComparingTo("30000.00");
        assertThat(total(schedule)).isEqualByComparingTo("32769.23");
    }

    @Test
    @DisplayName("the same term number means different money at each frequency")
    void twelveWeeklyIsNotTwelveMonthly() {
        BigDecimal principal = new BigDecimal("24000");
        BigDecimal rate = new BigDecimal("0.12");

        BigDecimal weekly = totalInterest(
                generator.generate(principal, rate, 12, WEEKLY, JAN_15));
        BigDecimal monthly = totalInterest(
                generator.generate(principal, rate, 12, MONTHLY, JAN_15));

        // 12 weeks is 12/52 of a year; 12 months is a whole year.
        assertThat(weekly).isEqualByComparingTo("664.62");
        assertThat(monthly).isEqualByComparingTo("2880.00");
    }

    @Test
    @DisplayName("a weekly schedule reconciles exactly, same as a monthly one")
    void weeklySchedulesAlsoReconcile() {
        List<Instalment> schedule = generator.generate(
                new BigDecimal("17500"), new BigDecimal("0.1375"), 26, WEEKLY, JAN_15);

        assertThat(totalPrincipal(schedule)).isEqualByComparingTo("17500.00");
        assertThat(schedule).allSatisfy(i -> assertThat(i.getAmountDue())
                .isEqualByComparingTo(i.getPrincipalDue().add(i.getInterestDue())));
    }

    // ── Rejected input ─────────────────────────────────────────────────────

    @Test
    @DisplayName("a zero-interest loan carries no interest on any row")
    void zeroRateIsAllowed() {
        List<Instalment> schedule = generator.generate(
                new BigDecimal("6000"), BigDecimal.ZERO, 6, MONTHLY, JAN_15);

        assertThat(schedule).allSatisfy(i ->
                assertThat(i.getInterestDue()).isEqualByComparingTo("0.00"));
        assertThat(total(schedule)).isEqualByComparingTo("6000.00");
    }

    @Test
    void rejectsPrincipalOfZeroOrLess() {
        assertThatThrownBy(() -> generator.generate(
                BigDecimal.ZERO, new BigDecimal("0.12"), 12, MONTHLY, JAN_15))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Principal");
    }

    @Test
    void rejectsANegativeRate() {
        assertThatThrownBy(() -> generator.generate(
                new BigDecimal("1000"), new BigDecimal("-0.01"), 12, MONTHLY, JAN_15))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("negative");
    }

    @Test
    void rejectsATermOfLessThanOneInstalment() {
        assertThatThrownBy(() -> generator.generate(
                new BigDecimal("1000"), new BigDecimal("0.12"), 0, MONTHLY, JAN_15))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Term");
    }

    @Test
    void rejectsAMissingFrequency() {
        assertThatThrownBy(() -> generator.generate(
                new BigDecimal("1000"), new BigDecimal("0.12"), 12, null, JAN_15))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("frequency");
    }

    private static BigDecimal total(List<Instalment> s) {
        return s.stream().map(Instalment::getAmountDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal totalPrincipal(List<Instalment> s) {
        return s.stream().map(Instalment::getPrincipalDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal totalInterest(List<Instalment> s) {
        return s.stream().map(Instalment::getInterestDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
