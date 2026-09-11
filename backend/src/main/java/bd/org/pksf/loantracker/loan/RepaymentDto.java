package bd.org.pksf.loantracker.loan;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RepaymentDto(Long id, String receiptNo, LocalDate receivedOn,
                           BigDecimal amount, String recordedBy) {

    public static RepaymentDto from(Repayment r) {
        return new RepaymentDto(r.getId(), r.getReceiptNo(), r.getReceivedOn(),
                r.getAmount(), r.getRecordedBy());
    }
}
