package io.github.rafijahiin.loantracker.support;

import io.github.rafijahiin.loantracker.borrower.BorrowerRepository;
import io.github.rafijahiin.loantracker.loan.LoanRepository;
import io.github.rafijahiin.loantracker.loan.RepaymentRepository;
import io.github.rafijahiin.loantracker.partner.PartnerRepository;
import io.github.rafijahiin.loantracker.user.AppUserRepository;
import org.springframework.stereotype.Component;

/**
 * Empties the database in foreign-key order.
 *
 * The order lives here, once, because it is easy to get wrong in a way that
 * only shows up on one engine. A smoke test that deleted partners while
 * borrowers still referenced them passed against H2 for days and failed the
 * moment the same suite ran on PostgreSQL, which enforces the constraint
 * strictly. Two test classes each keeping their own idea of the order is how
 * that happens.
 */
@Component
public class DatabaseCleaner {

    private final RepaymentRepository repayments;
    private final LoanRepository loans;
    private final BorrowerRepository borrowers;
    private final AppUserRepository users;
    private final PartnerRepository partners;

    public DatabaseCleaner(RepaymentRepository repayments, LoanRepository loans,
                           BorrowerRepository borrowers, AppUserRepository users,
                           PartnerRepository partners) {
        this.repayments = repayments;
        this.loans = loans;
        this.borrowers = borrowers;
        this.users = users;
        this.partners = partners;
    }

    /** Children before parents. Instalments are not deleted explicitly: they
     *  cascade from the loan, which is also how the application deletes them. */
    public void clean() {
        repayments.deleteAll();
        loans.deleteAll();
        borrowers.deleteAll();
        users.deleteAll();
        partners.deleteAll();
    }
}
