package bd.org.pksf.loantracker.loan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record LoanDto(
        Long id,
        String loanNumber,
        Long borrowerId,
        String borrowerName,
        BigDecimal principal,
        BigDecimal annualRate,
        int termMonths,
        LocalDate disbursedOn,
        LoanStatus status,
        BigDecimal totalDue,
        BigDecimal totalPaid,
        BigDecimal outstanding,
        BigDecimal overdue,
        long daysInArrears,
        List<InstalmentDto> schedule) {

    /** Summary form: no schedule. A list endpoint returning every instalment of
     *  every loan turns a twenty-row page into hundreds of rows of JSON that
     *  the list view does not draw. */
    public static LoanDto summary(Loan l, LocalDate asOf) {
        return build(l, asOf, null);
    }

    public static LoanDto detail(Loan l, LocalDate asOf) {
        return build(l, asOf, l.getInstalments().stream()
                .map(InstalmentDto::from).toList());
    }

    private static LoanDto build(Loan l, LocalDate asOf, List<InstalmentDto> schedule) {
        return new LoanDto(
                l.getId(), l.getLoanNumber(),
                l.getBorrower() == null ? null : l.getBorrower().getId(),
                l.getBorrower() == null ? null : l.getBorrower().getName(),
                l.getPrincipal(), l.getAnnualRate(), l.getTermMonths(),
                l.getDisbursedOn(), l.getStatus(),
                l.getTotalDue(), l.getTotalPaid(), l.getOutstanding(),
                l.getOverdueAmount(asOf), l.getDaysInArrears(asOf), schedule);
    }
}
