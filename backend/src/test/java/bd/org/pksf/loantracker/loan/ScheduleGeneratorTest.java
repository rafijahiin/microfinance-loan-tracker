package bd.org.pksf.loantracker.loan;

import bd.org.pksf.loantracker.common.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScheduleGeneratorTest {

    private final ScheduleGenerator generator = new ScheduleGenerator();

    private static final LocalDate JAN_15 = LocalDate.of(2026, 1, 15);

    @Test
    @DisplayName("flat interest is principal x rate x years, split evenly")
    void flatInterestIsChargedOnTheOriginalPrincipal() {
        // 30,000 at 12 percent flat over 12 months is 3,600 of interest, so
        // 33,600 payable in 12 instalments of 2,800.
        List<Instalment> schedule = generator.generate(
                new BigDecimal("30000"), new BigDecimal("0.12"), 12, JAN_15);

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
                new BigDecimal("10000"), BigDecimal.ZERO, 3, JAN_15);

        assertThat(totalPrincipal(schedule)).isEqualByComparingTo("10000.00");
        assertThat(total(schedule)).isEqualByComparingTo("10000.00");
    }

    @Test
    @DisplayName("the final instalment absorbs the rounding remainder")
    void theRemainderLandsOnTheLastInstalment() {
        List<Instalment> schedule = generator.generate(
                new BigDecimal("10000"), BigDecimal.ZERO, 3, JAN_15);

        assertThat(schedule.get(0).getAmountDue()).isEqualByComparingTo("3333.33");
        assertThat(schedule.get(1).getAmountDue()).isEqualByComparingTo("3333.33");
        assertThat(schedule.get(2).getAmountDue()).isEqualByComparingTo("3333.34");
    }

    @Test
    @DisplayName("principal and interest on each row sum to the row total")
    void componentsAgreeWithTheInstalmentAmount() {
        List<Instalment> schedule = generator.generate(
                new BigDecimal("47350"), new BigDecimal("0.1375"), 7, JAN_15);

        assertThat(schedule).allSatisfy(i -> assertThat(i.getAmountDue())
                .isEqualByComparingTo(i.getPrincipalDue().add(i.getInterestDue())));
    }

    @Test
    @DisplayName("instalments fall due monthly from the disbursement date")
    void dueDatesAreMonthlyFromDisbursement() {
        List<Instalment> schedule = generator.generate(
                new BigDecimal("12000"), BigDecimal.ZERO, 3, JAN_15);

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
                new BigDecimal("9000"), BigDecimal.ZERO, 3,
                LocalDate.of(2026, 1, 31));

        assertThat(schedule.get(0).getDueOn()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(schedule.get(1).getDueOn()).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(schedule.get(2).getDueOn()).isEqualTo(LocalDate.of(2026, 4, 30));
    }

    @Test
    @DisplayName("a zero-interest loan carries no interest on any row")
    void zeroRateIsAllowed() {
        List<Instalment> schedule = generator.generate(
                new BigDecimal("6000"), BigDecimal.ZERO, 6, JAN_15);

        assertThat(schedule).allSatisfy(i ->
                assertThat(i.getInterestDue()).isEqualByComparingTo("0.00"));
        assertThat(total(schedule)).isEqualByComparingTo("6000.00");
    }

    @Test
    void rejectsPrincipalOfZeroOrLess() {
        assertThatThrownBy(() -> generator.generate(
                BigDecimal.ZERO, new BigDecimal("0.12"), 12, JAN_15))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Principal");
    }

    @Test
    void rejectsANegativeRate() {
        assertThatThrownBy(() -> generator.generate(
                new BigDecimal("1000"), new BigDecimal("-0.01"), 12, JAN_15))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("negative");
    }

    @Test
    void rejectsATermOfLessThanOneMonth() {
        assertThatThrownBy(() -> generator.generate(
                new BigDecimal("1000"), new BigDecimal("0.12"), 0, JAN_15))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Term");
    }

    private static BigDecimal total(List<Instalment> s) {
        return s.stream().map(Instalment::getAmountDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal totalPrincipal(List<Instalment> s) {
        return s.stream().map(Instalment::getPrincipalDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
