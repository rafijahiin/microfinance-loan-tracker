package io.github.rafijahiin.loantracker.loan;

import io.github.rafijahiin.loantracker.borrower.Borrower;
import io.github.rafijahiin.loantracker.common.Auditable;
import io.github.rafijahiin.loantracker.common.Money;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "loan")
public class Loan extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Optimistic lock on the whole loan.
     *
     * Two clerks at the same branch posting against one loan in the same second
     * is not a thought experiment, it is a Thursday. Without this the second
     * write wins silently and one receipt vanishes from the balance while
     * remaining in the cash book, which is the worst kind of discrepancy
     * because nothing errors.
     *
     * Note what this does NOT do on its own: changing an instalment does not
     * make the loan row dirty, so Hibernate would never bump this version on an
     * ordinary repayment. RepaymentService forces the increment explicitly. See
     * the comment there.
     */
    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "loan_number", nullable = false, unique = true, length = 40)
    private String loanNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "borrower_id", nullable = false)
    private Borrower borrower;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal principal;

    /** Flat annual rate as a fraction, so 0.12 is 12 percent. Stored at 4
     *  decimal places because service charges here are quoted to a quarter of a
     *  percent and 2 places would round 12.25 percent away. */
    @Column(name = "annual_rate", nullable = false, precision = 6, scale = 4)
    private BigDecimal annualRate;

    /** Number of instalments, not months. What a period means is decided by
     *  `frequency`: 40 weekly periods and 40 monthly periods are different
     *  loans, and the old termMonths field could not express the first. */
    @Column(name = "term_periods", nullable = false)
    private int termPeriods;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RepaymentFrequency frequency = RepaymentFrequency.MONTHLY;

    @Column(name = "disbursed_on", nullable = false)
    private LocalDate disbursedOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LoanStatus status = LoanStatus.ACTIVE;

    /** Cascade because an instalment has no meaning apart from its loan, and
     *  orphanRemoval so that regenerating a schedule cannot leave stranded rows
     *  that would still be counted in the portfolio totals. */
    @OneToMany(mappedBy = "loan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("instalmentNo asc")
    private List<Instalment> instalments = new ArrayList<>();

    protected Loan() {
    }

    public Loan(String loanNumber, Borrower borrower, BigDecimal principal,
                BigDecimal annualRate, int termPeriods,
                RepaymentFrequency frequency, LocalDate disbursedOn) {
        this.loanNumber = loanNumber;
        this.borrower = borrower;
        this.principal = Money.normalise(principal);
        this.annualRate = annualRate;
        this.termPeriods = termPeriods;
        this.frequency = frequency;
        this.disbursedOn = disbursedOn;
    }

    public void addInstalment(Instalment i) {
        instalments.add(i);
        i.setLoan(this);
    }

    public Long getId() {
        return id;
    }

    public Long getVersion() {
        return version;
    }

    public String getLoanNumber() {
        return loanNumber;
    }

    public Borrower getBorrower() {
        return borrower;
    }

    public BigDecimal getPrincipal() {
        return principal;
    }

    public BigDecimal getAnnualRate() {
        return annualRate;
    }

    public int getTermPeriods() {
        return termPeriods;
    }

    public RepaymentFrequency getFrequency() {
        return frequency;
    }

    public LocalDate getDisbursedOn() {
        return disbursedOn;
    }

    public LoanStatus getStatus() {
        return status;
    }

    public void setStatus(LoanStatus status) {
        this.status = status;
    }

    public List<Instalment> getInstalments() {
        return instalments;
    }

    // ── Derived figures. Computed from the instalments rather than stored, so
    //    a balance can never drift out of step with the schedule that backs it.

    public BigDecimal getTotalDue() {
        return Money.normalise(instalments.stream()
                .map(Instalment::getAmountDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    public BigDecimal getTotalPaid() {
        return Money.normalise(instalments.stream()
                .map(Instalment::getAmountPaid)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    public BigDecimal getOutstanding() {
        return Money.normalise(getTotalDue().subtract(getTotalPaid()));
    }

    /** Amount past its due date and still unpaid. Distinct from outstanding,
     *  which includes instalments that are not due yet. */
    public BigDecimal getOverdueAmount(LocalDate asOf) {
        return Money.normalise(instalments.stream()
                .filter(i -> i.isOverdue(asOf))
                .map(Instalment::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    /** Days since the OLDEST unpaid instalment fell due, which is the figure
     *  portfolio-at-risk buckets are cut on. Using the newest would flatter the
     *  portfolio by resetting the clock on every fresh instalment. */
    public long getDaysInArrears(LocalDate asOf) {
        return instalments.stream()
                .filter(i -> i.isOverdue(asOf))
                .mapToLong(i -> java.time.temporal.ChronoUnit.DAYS
                        .between(i.getDueOn(), asOf))
                .max()
                .orElse(0L);
    }
}
