package bd.org.pksf.loantracker.loan;

import bd.org.pksf.loantracker.common.BusinessRuleException;
import bd.org.pksf.loantracker.common.Money;
import bd.org.pksf.loantracker.security.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
public class RepaymentService {

    private final LoanService loanService;
    private final LoanRepository loans;
    private final RepaymentRepository repayments;

    public RepaymentService(LoanService loanService, LoanRepository loans,
                            RepaymentRepository repayments) {
        this.loanService = loanService;
        this.loans = loans;
        this.repayments = repayments;
    }

    /**
     * Records a payment and settles it against the schedule, oldest instalment
     * first.
     *
     * Oldest-first matters. Applying a payment to the instalment that happens
     * to be due this month would leave an older one open, and the loan would go
     * on reporting as in arrears while the member is paying every week. It also
     * understates recovery in the portfolio-at-risk figure, because arrears age
     * is measured from the oldest unsettled instalment.
     *
     * Overpayment is refused rather than quietly held as a credit. A member
     * handing over more than the loan owes is nearly always a keying error at
     * the branch, and turning it into an unexplained credit balance is how a
     * ledger stops reconciling.
     */
    @Transactional
    public Repayment record(AuthenticatedUser caller, Long loanId, String receiptNo,
                            BigDecimal amount, LocalDate receivedOn) {

        Loan loan = loanService.get(caller, loanId);

        if (!Money.isPositive(amount)) {
            throw new BusinessRuleException("A repayment must be greater than zero");
        }
        if (loan.getStatus() == LoanStatus.CLOSED) {
            throw new BusinessRuleException("This loan is already settled");
        }
        if (loan.getStatus() == LoanStatus.WRITTEN_OFF) {
            throw new BusinessRuleException(
                    "This loan is written off. Recoveries on a written-off loan are "
                    + "posted separately, not against its schedule.");
        }
        if (receivedOn.isBefore(loan.getDisbursedOn())) {
            throw new BusinessRuleException(
                    "A repayment cannot be dated before the loan was disbursed");
        }
        if (repayments.existsByReceiptNo(receiptNo)) {
            throw new BusinessRuleException("Receipt " + receiptNo + " has already been posted");
        }

        BigDecimal outstanding = loan.getOutstanding();
        BigDecimal payment = Money.normalise(amount);
        if (payment.compareTo(outstanding) > 0) {
            throw new BusinessRuleException(
                    "Payment of " + payment + " is more than the " + outstanding
                    + " still owed on this loan");
        }

        List<Instalment> schedule = loan.getInstalments().stream()
                .sorted(Comparator.comparingInt(Instalment::getInstalmentNo))
                .toList();

        BigDecimal remaining = payment;
        for (Instalment instalment : schedule) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            remaining = Money.normalise(remaining.subtract(
                    instalment.apply(remaining, receivedOn)));
        }

        if (loan.getOutstanding().compareTo(BigDecimal.ZERO) <= 0) {
            loan.setStatus(LoanStatus.CLOSED);
        }
        loans.save(loan);

        return repayments.save(new Repayment(loan, receiptNo, receivedOn, payment,
                caller.email()));
    }

    @Transactional(readOnly = true)
    public List<Repayment> history(AuthenticatedUser caller, Long loanId) {
        loanService.get(caller, loanId);
        return repayments.findByLoanIdOrderByReceivedOnAscIdAsc(loanId);
    }
}
