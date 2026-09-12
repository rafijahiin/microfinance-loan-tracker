package io.github.rafijahiin.loantracker.config;

import io.github.rafijahiin.loantracker.borrower.Borrower;
import io.github.rafijahiin.loantracker.audit.AuditAction;
import io.github.rafijahiin.loantracker.audit.AuditService;
import io.github.rafijahiin.loantracker.borrower.BorrowerRepository;
import io.github.rafijahiin.loantracker.borrower.NationalIdProtector;
import io.github.rafijahiin.loantracker.loan.*;
import io.github.rafijahiin.loantracker.partner.PartnerOrganisation;
import io.github.rafijahiin.loantracker.partner.PartnerRepository;
import io.github.rafijahiin.loantracker.user.AppUser;
import io.github.rafijahiin.loantracker.user.AppUserRepository;
import io.github.rafijahiin.loantracker.security.AuthenticatedUser;
import io.github.rafijahiin.loantracker.user.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Demo data, so the stack comes up with something to look at.
 *
 * Gated on app.seed-demo-data and off by default, because a seeder that runs
 * unconditionally will eventually run against a real database. Passwords are
 * encoded here rather than shipped as literal bcrypt hashes in a migration:
 * a hash in version control is a credential in version control.
 */
@Configuration
@ConditionalOnProperty(name = "app.seed-demo-data", havingValue = "true")
public class DemoDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    @Bean
    public ApplicationRunner seedDemoData(PartnerRepository partners,
                                          AppUserRepository users,
                                          BorrowerRepository borrowers,
                                          LoanRepository loans,
                                          RepaymentRepository repayments,
                                          ScheduleGenerator scheduleGenerator,
                                          NationalIdProtector nid,
                                          AuditService audit,
                                          PasswordEncoder encoder,
                                          PlatformTransactionManager txManager) {
        // Wrapped explicitly rather than annotated.
        //
        // This method used to be `@Transactional protected void seed(...)`
        // called from the lambda below, which does nothing at all: Spring's
        // transactional behaviour comes from a proxy, and a call from inside
        // the same class never goes through it. The seeder had been running
        // with no transaction since it was written, and nothing noticed because
        // nothing needed one. Writing audit entries with MANDATORY propagation
        // is what finally made it fail loudly.
        TransactionTemplate tx = new TransactionTemplate(txManager);
        return args -> tx.executeWithoutResult(status ->
                seed(partners, users, borrowers, loans, repayments,
                        scheduleGenerator, nid, audit, encoder));
    }

    private void seed(PartnerRepository partners, AppUserRepository users,
                        BorrowerRepository borrowers, LoanRepository loans,
                        RepaymentRepository repayments,
                        ScheduleGenerator scheduleGenerator,
                        NationalIdProtector nid,
                        AuditService audit,
                        PasswordEncoder encoder) {

        if (partners.count() > 0) {
            log.info("Demo data already present, skipping seed.");
            return;
        }

        PartnerOrganisation shomota = partners.save(
                new PartnerOrganisation("PO-001", "Shomota Unnayan Sangstha", "Rangpur"));
        PartnerOrganisation nodi = partners.save(
                new PartnerOrganisation("PO-002", "Nodi Jonopod Foundation", "Barishal"));

        users.save(new AppUser("admin@example.org",
                encoder.encode("admin12345"), Role.ADMIN, null));
        users.save(new AppUser("officer.rangpur@example.org",
                encoder.encode("officer12345"), Role.PO_OFFICER, shomota));
        users.save(new AppUser("officer.barishal@example.org",
                encoder.encode("officer12345"), Role.PO_OFFICER, nodi));

        LocalDate today = LocalDate.now();

        // Loans are dated back by different amounts so the portfolio has a real
        // shape: one paid off, one current, one a month late, one badly late.
        // A seed where every loan is healthy makes the PAR figure untestable by
        // eye and hides whatever the arrears logic gets wrong.
        record Seed(PartnerOrganisation po, String code, String name, String village,
                    String nid, String loanNo, String principal, String rate,
                    int term, RepaymentFrequency frequency,
                    int disbursedMonthsAgo, int instalmentsPaid) {
        }

        // A deliberate mix of weekly and monthly, because the portfolio figures
        // are only convincing if both products appear in them.
        List<Seed> seeds = List.of(
                new Seed(shomota, "M-0001", "Rahima Begum", "Kaunia", "1990111000001",
                        "L-2026-0001", "30000", "0.1200", 40, RepaymentFrequency.WEEKLY,
                        13, 40),
                new Seed(shomota, "M-0002", "Shafiqul Islam", "Pirgacha", "1988222000002",
                        "L-2026-0002", "50000", "0.1200", 12, RepaymentFrequency.MONTHLY,
                        4, 4),
                new Seed(shomota, "M-0003", "Nasima Akter", "Badarganj", "1995333000003",
                        "L-2026-0003", "25000", "0.1500", 40, RepaymentFrequency.WEEKLY,
                        5, 12),
                new Seed(nodi, "M-0001", "Jahanara Khatun", "Mehendiganj", "1992444000004",
                        "L-2026-0004", "40000", "0.1200", 12, RepaymentFrequency.MONTHLY,
                        8, 3),
                new Seed(nodi, "M-0002", "Abdul Mannan", "Hizla", "1985555000005",
                        "L-2026-0005", "60000", "0.1000", 18, RepaymentFrequency.MONTHLY,
                        2, 2),
                new Seed(nodi, "M-0003", "Rokeya Sultana", "Muladi", "1998666000006",
                        "L-2026-0006", "35000", "0.1200", 26, RepaymentFrequency.WEEKLY,
                        1, 0));

        // The seeded portfolio gets a trail too, attributed to the officer who
        // would have done the work. Without it the activity feed is empty on
        // first run and the feature looks unbuilt rather than unused.
        java.util.Map<Long, AuthenticatedUser> actors = java.util.Map.of(
                shomota.getId(), new AuthenticatedUser(
                        2L, "officer.rangpur@example.org", Role.PO_OFFICER,
                        shomota.getId()),
                nodi.getId(), new AuthenticatedUser(
                        3L, "officer.barishal@example.org", Role.PO_OFFICER,
                        nodi.getId()));

        int receipt = 1;
        for (Seed s : seeds) {
            LocalDate disbursed = today.minusMonths(s.disbursedMonthsAgo());
            Borrower b = new Borrower(s.po(), s.code(), s.name(),
                    s.po().getDistrict(), disbursed.minusMonths(2));
            b.setVillage(s.village());
            b.setNationalId(nid.hash(s.nid()), nid.mask(s.nid()));
            b = borrowers.save(b);

            Loan loan = new Loan(s.loanNo(), b, new BigDecimal(s.principal()),
                    new BigDecimal(s.rate()), s.term(), s.frequency(), disbursed);
            scheduleGenerator.generate(loan.getPrincipal(), loan.getAnnualRate(),
                            loan.getTermPeriods(), loan.getFrequency(), disbursed)
                    .forEach(loan::addInstalment);
            loans.save(loan);

            for (int i = 0; i < s.instalmentsPaid(); i++) {
                Instalment instalment = loan.getInstalments().get(i);
                BigDecimal amount = instalment.getAmountDue();
                LocalDate paidOn = instalment.getDueOn();
                instalment.apply(amount, paidOn);
                repayments.save(new Repayment(loan,
                        String.format("R-%05d", receipt++), paidOn, amount,
                        "seed"));
            }
            if (loan.getOutstanding().compareTo(BigDecimal.ZERO) <= 0) {
                loan.setStatus(LoanStatus.CLOSED);
            }
            loans.save(loan);

            AuthenticatedUser actor = actors.get(s.po().getId());
            audit.record(actor, AuditAction.MEMBER_ENROLLED, s.po().getId(),
                    AuditService.ENTITY_BORROWER, b.getId(),
                    "%s enrolled as %s (national ID %s)".formatted(
                            b.getName(), b.getMemberCode(), b.getNationalIdMasked()),
                    null);
            audit.record(actor, AuditAction.LOAN_DISBURSED, s.po().getId(),
                    AuditService.ENTITY_LOAN, loan.getId(),
                    "%s disbursed to %s: %s over %d %s instalments".formatted(
                            loan.getLoanNumber(), b.getName(),
                            loan.getPrincipal().toPlainString(),
                            loan.getTermPeriods(),
                            loan.getFrequency().name().toLowerCase()),
                    loan.getPrincipal());
            if (s.instalmentsPaid() > 0) {
                audit.record(actor, AuditAction.REPAYMENT_POSTED, s.po().getId(),
                        AuditService.ENTITY_LOAN, loan.getId(),
                        "%d instalments collected against %s, leaving %s outstanding"
                                .formatted(s.instalmentsPaid(), loan.getLoanNumber(),
                                        loan.getOutstanding().toPlainString()),
                        loan.getTotalPaid());
            }
        }

        log.info("Seeded {} partners, {} users, {} borrowers, {} loans.",
                partners.count(), users.count(), borrowers.count(), loans.count());
    }
}
