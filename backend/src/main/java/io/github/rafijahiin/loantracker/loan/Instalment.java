package io.github.rafijahiin.loantracker.loan;

import io.github.rafijahiin.loantracker.common.Auditable;
import io.github.rafijahiin.loantracker.common.Money;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "instalment",
       uniqueConstraints = @UniqueConstraint(
               name = "uq_instalment_loan_no",
               columnNames = {"loan_id", "instalment_no"}))
public class Instalment extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loan_id", nullable = false)
    private Loan loan;

    @Column(name = "instalment_no", nullable = false)
    private int instalmentNo;

    @Column(name = "due_on", nullable = false)
    private LocalDate dueOn;

    @Column(name = "principal_due", nullable = false, precision = 15, scale = 2)
    private BigDecimal principalDue;

    @Column(name = "interest_due", nullable = false, precision = 15, scale = 2)
    private BigDecimal interestDue;

    @Column(name = "amount_due", nullable = false, precision = 15, scale = 2)
    private BigDecimal amountDue;

    @Column(name = "amount_paid", nullable = false, precision = 15, scale = 2)
    private BigDecimal amountPaid = Money.zero();

    @Column(name = "settled_on")
    private LocalDate settledOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InstalmentStatus status = InstalmentStatus.PENDING;

    protected Instalment() {
    }

    public Instalment(int instalmentNo, LocalDate dueOn, BigDecimal principalDue,
                      BigDecimal interestDue) {
        this.instalmentNo = instalmentNo;
        this.dueOn = dueOn;
        this.principalDue = Money.normalise(principalDue);
        this.interestDue = Money.normalise(interestDue);
        this.amountDue = Money.normalise(principalDue.add(interestDue));
        this.amountPaid = Money.zero();
    }

    /** Applies up to `available` and returns what it actually took, so the
     *  caller can carry the rest to the next instalment. Returning the consumed
     *  amount rather than mutating a shared remainder keeps the allocation loop
     *  honest and easy to test. */
    public BigDecimal apply(BigDecimal available, LocalDate on) {
        BigDecimal balance = getBalance();
        if (balance.compareTo(BigDecimal.ZERO) <= 0
                || available.compareTo(BigDecimal.ZERO) <= 0) {
            return Money.zero();
        }
        BigDecimal taken = available.min(balance);
        this.amountPaid = Money.normalise(this.amountPaid.add(taken));
        if (getBalance().compareTo(BigDecimal.ZERO) <= 0) {
            this.status = InstalmentStatus.PAID;
            this.settledOn = on;
        } else {
            this.status = InstalmentStatus.PARTIAL;
        }
        return Money.normalise(taken);
    }

    public BigDecimal getBalance() {
        return Money.normalise(amountDue.subtract(amountPaid));
    }

    public boolean isSettled() {
        return status == InstalmentStatus.PAID;
    }

    public boolean isOverdue(LocalDate asOf) {
        return !isSettled() && dueOn.isBefore(asOf);
    }

    public Long getId() {
        return id;
    }

    public Loan getLoan() {
        return loan;
    }

    void setLoan(Loan loan) {
        this.loan = loan;
    }

    public int getInstalmentNo() {
        return instalmentNo;
    }

    public LocalDate getDueOn() {
        return dueOn;
    }

    public BigDecimal getPrincipalDue() {
        return principalDue;
    }

    public BigDecimal getInterestDue() {
        return interestDue;
    }

    public BigDecimal getAmountDue() {
        return amountDue;
    }

    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    public LocalDate getSettledOn() {
        return settledOn;
    }

    public InstalmentStatus getStatus() {
        return status;
    }
}
