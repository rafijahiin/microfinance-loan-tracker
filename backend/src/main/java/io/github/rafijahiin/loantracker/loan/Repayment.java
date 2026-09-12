package io.github.rafijahiin.loantracker.loan;

import io.github.rafijahiin.loantracker.common.Auditable;
import io.github.rafijahiin.loantracker.common.Money;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One payment as it was actually received at the branch.
 *
 *  Kept as its own row rather than only adjusting instalment balances, because
 *  a member pays a sum of money on a day, and that event is what a receipt and
 *  a cash book record. How it was split across instalments is a derivation from
 *  it. Storing only the split would make it impossible to answer "what did she
 *  hand over on Tuesday", which is the question an audit actually asks.
 */
@Entity
@Table(name = "repayment")
public class Repayment extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loan_id", nullable = false)
    private Loan loan;

    @Column(name = "receipt_no", nullable = false, unique = true, length = 40)
    private String receiptNo;

    @Column(name = "received_on", nullable = false)
    private LocalDate receivedOn;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "recorded_by", length = 200)
    private String recordedBy;

    protected Repayment() {
    }

    public Repayment(Loan loan, String receiptNo, LocalDate receivedOn,
                     BigDecimal amount, String recordedBy) {
        this.loan = loan;
        this.receiptNo = receiptNo;
        this.receivedOn = receivedOn;
        this.amount = Money.normalise(amount);
        this.recordedBy = recordedBy;
    }

    public Long getId() {
        return id;
    }

    public Loan getLoan() {
        return loan;
    }

    public String getReceiptNo() {
        return receiptNo;
    }

    public LocalDate getReceivedOn() {
        return receivedOn;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getRecordedBy() {
        return recordedBy;
    }
}
