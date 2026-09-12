package io.github.rafijahiin.loantracker.loan;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InstalmentDto(int instalmentNo, LocalDate dueOn,
                            BigDecimal principalDue, BigDecimal interestDue,
                            BigDecimal amountDue, BigDecimal amountPaid,
                            BigDecimal balance, InstalmentStatus status,
                            LocalDate settledOn) {

    public static InstalmentDto from(Instalment i) {
        return new InstalmentDto(i.getInstalmentNo(), i.getDueOn(),
                i.getPrincipalDue(), i.getInterestDue(), i.getAmountDue(),
                i.getAmountPaid(), i.getBalance(), i.getStatus(), i.getSettledOn());
    }
}
