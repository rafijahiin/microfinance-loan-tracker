package io.github.rafijahiin.loantracker.loan;

import io.github.rafijahiin.loantracker.borrower.Borrower;
import io.github.rafijahiin.loantracker.borrower.BorrowerRepository;
import io.github.rafijahiin.loantracker.common.BusinessRuleException;
import io.github.rafijahiin.loantracker.common.NotFoundException;
import io.github.rafijahiin.loantracker.security.AccessGuard;
import io.github.rafijahiin.loantracker.security.AuthenticatedUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class LoanService {

    private final LoanRepository loans;
    private final BorrowerRepository borrowers;
    private final ScheduleGenerator scheduleGenerator;
    private final AccessGuard guard;

    public LoanService(LoanRepository loans, BorrowerRepository borrowers,
                       ScheduleGenerator scheduleGenerator, AccessGuard guard) {
        this.loans = loans;
        this.borrowers = borrowers;
        this.scheduleGenerator = scheduleGenerator;
        this.guard = guard;
    }

    @Transactional
    public Loan disburse(AuthenticatedUser caller, Long borrowerId, String loanNumber,
                         BigDecimal principal, BigDecimal annualRate, int termPeriods,
                         RepaymentFrequency frequency, LocalDate disbursedOn) {

        Borrower borrower = borrowers.findByIdWithPartner(borrowerId)
                .orElseThrow(() -> new NotFoundException("Borrower", borrowerId));
        guard.assertCanAccess(caller, borrower.getPartnerId(), "Borrower", borrowerId);

        if (loans.existsByLoanNumber(loanNumber)) {
            throw new BusinessRuleException("Loan number " + loanNumber + " is already used");
        }
        if (disbursedOn.isBefore(borrower.getEnrolledOn())) {
            throw new BusinessRuleException(
                    "A loan cannot be disbursed before the borrower was enrolled");
        }

        Loan loan = new Loan(loanNumber, borrower, principal, annualRate,
                termPeriods, frequency, disbursedOn);
        scheduleGenerator.generate(principal, annualRate, termPeriods, frequency,
                        disbursedOn)
                .forEach(loan::addInstalment);

        return loans.save(loan);
    }

    @Transactional(readOnly = true)
    public Loan get(AuthenticatedUser caller, Long loanId) {
        Loan loan = loans.findByIdWithSchedule(loanId)
                .orElseThrow(() -> new NotFoundException("Loan", loanId));
        guard.assertCanAccess(caller, loan.getBorrower().getPartnerId(), "Loan", loanId);
        return loan;
    }

    @Transactional(readOnly = true)
    public Page<Loan> search(AuthenticatedUser caller, LoanStatus status, Pageable pageable) {
        Page<Long> ids = loans.searchIds(guard.scopeOf(caller), status, pageable);
        if (ids.isEmpty()) {
            // `in ()` is not valid SQL, so an empty page must not reach the
            // second query.
            return new PageImpl<>(List.of(), pageable, ids.getTotalElements());
        }

        Map<Long, Loan> fetched = loans.findAllWithScheduleByIds(ids.getContent())
                .stream()
                .collect(Collectors.toMap(Loan::getId, Function.identity()));

        // `in :ids` does not preserve the sort the page was built with, so the
        // rows are put back in the order the first query returned them.
        List<Loan> ordered = ids.getContent().stream()
                .map(fetched::get)
                .filter(Objects::nonNull)
                .toList();

        return new PageImpl<>(ordered, pageable, ids.getTotalElements());
    }

    @Transactional(readOnly = true)
    public List<Loan> forBorrower(AuthenticatedUser caller, Long borrowerId) {
        Borrower borrower = borrowers.findByIdWithPartner(borrowerId)
                .orElseThrow(() -> new NotFoundException("Borrower", borrowerId));
        guard.assertCanAccess(caller, borrower.getPartnerId(), "Borrower", borrowerId);
        return loans.findByBorrowerId(borrowerId);
    }

    /** Writing off is restricted to ADMIN at the controller. It is an
     *  accounting decision by the apex body, not something a branch clerk does
     *  to tidy an ageing report. */
    @Transactional
    public Loan writeOff(AuthenticatedUser caller, Long loanId) {
        Loan loan = get(caller, loanId);
        if (loan.getStatus() == LoanStatus.CLOSED) {
            throw new BusinessRuleException("A settled loan cannot be written off");
        }
        if (loan.getStatus() == LoanStatus.WRITTEN_OFF) {
            throw new BusinessRuleException("This loan is already written off");
        }
        loan.setStatus(LoanStatus.WRITTEN_OFF);
        return loans.save(loan);
    }
}
