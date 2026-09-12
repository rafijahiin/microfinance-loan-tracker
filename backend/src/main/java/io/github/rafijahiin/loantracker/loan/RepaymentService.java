package io.github.rafijahiin.loantracker.loan;

import io.github.rafijahiin.loantracker.audit.AuditAction;
import io.github.rafijahiin.loantracker.audit.AuditService;
import io.github.rafijahiin.loantracker.common.BusinessRuleException;
import io.github.rafijahiin.loantracker.common.Money;
import io.github.rafijahiin.loantracker.security.AuthenticatedUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
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
    private final EntityManager entityManager;
    private final AuditService audit;

    public RepaymentService(LoanService loanService, LoanRepository loans,
                            RepaymentRepository repayments,
                            EntityManager entityManager, AuditService audit) {
        this.loanService = loanService;
        this.loans = loans;
        this.repayments = repayments;
        this.entityManager = entityManager;
        this.audit = audit;
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

        // Take the optimistic lock BEFORE reading the balance the decision is
        // made on. A repayment usually changes only instalment rows, and
        // modifying a child collection does not make the parent row dirty, so
        // @Version on Loan would never advance by itself and two concurrent
        // payments would both believe the same outstanding figure.
        //
        // OPTIMISTIC_FORCE_INCREMENT bumps the loan's version at commit even
        // when the loan row is otherwise untouched, so the second transaction
        // to commit fails instead of silently overwriting the first.
        entityManager.lock(loan, LockModeType.OPTIMISTIC_FORCE_INCREMENT);

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

        Repayment saved = repayments.save(new Repayment(loan, receiptNo, receivedOn,
                payment, caller.email()));

        audit.record(caller, AuditAction.REPAYMENT_POSTED,
                loan.getBorrower().getPartnerId(),
                AuditService.ENTITY_LOAN, loan.getId(),
                "Receipt %s: %s posted against %s, leaving %s outstanding".formatted(
                        receiptNo, payment.toPlainString(), loan.getLoanNumber(),
                        loan.getOutstanding().toPlainString()),
                payment);

        return saved;
    }

    @Transactional(readOnly = true)
    public List<Repayment> history(AuthenticatedUser caller, Long loanId) {
        loanService.get(caller, loanId);
        return repayments.findByLoanIdOrderByReceivedOnAscIdAsc(loanId);
    }
}
