package bd.org.pksf.loantracker.config;

import bd.org.pksf.loantracker.borrower.Borrower;
import bd.org.pksf.loantracker.borrower.BorrowerRepository;
import bd.org.pksf.loantracker.loan.*;
import bd.org.pksf.loantracker.partner.PartnerOrganisation;
import bd.org.pksf.loantracker.partner.PartnerRepository;
import bd.org.pksf.loantracker.user.AppUser;
import bd.org.pksf.loantracker.user.AppUserRepository;
import bd.org.pksf.loantracker.user.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

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
                                          PasswordEncoder encoder) {
        return args -> seed(partners, users, borrowers, loans, repayments,
                scheduleGenerator, encoder);
    }

    @Transactional
    protected void seed(PartnerRepository partners, AppUserRepository users,
                        BorrowerRepository borrowers, LoanRepository loans,
                        RepaymentRepository repayments,
                        ScheduleGenerator scheduleGenerator,
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
                    String loanNo, String principal, String rate, int term,
                    int disbursedMonthsAgo, int instalmentsPaid) {
        }

        List<Seed> seeds = List.of(
                new Seed(shomota, "M-0001", "Rahima Begum", "Kaunia",
                        "L-2026-0001", "30000", "0.1200", 12, 13, 12),
                new Seed(shomota, "M-0002", "Shafiqul Islam", "Pirgacha",
                        "L-2026-0002", "50000", "0.1200", 12, 4, 4),
                new Seed(shomota, "M-0003", "Nasima Akter", "Badarganj",
                        "L-2026-0003", "25000", "0.1500", 10, 5, 3),
                new Seed(nodi, "M-0001", "Jahanara Khatun", "Mehendiganj",
                        "L-2026-0004", "40000", "0.1200", 12, 8, 3),
                new Seed(nodi, "M-0002", "Abdul Mannan", "Hizla",
                        "L-2026-0005", "60000", "0.1000", 18, 2, 2),
                new Seed(nodi, "M-0003", "Rokeya Sultana", "Muladi",
                        "L-2026-0006", "35000", "0.1200", 12, 1, 0));

        int receipt = 1;
        for (Seed s : seeds) {
            LocalDate disbursed = today.minusMonths(s.disbursedMonthsAgo());
            Borrower b = borrowers.save(new Borrower(s.po(), s.code(), s.name(),
                    s.po().getDistrict(), disbursed.minusMonths(2)));
            b.setVillage(s.village());

            Loan loan = new Loan(s.loanNo(), b, new BigDecimal(s.principal()),
                    new BigDecimal(s.rate()), s.term(), disbursed);
            scheduleGenerator.generate(loan.getPrincipal(), loan.getAnnualRate(),
                    loan.getTermMonths(), disbursed).forEach(loan::addInstalment);
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
        }

        log.info("Seeded {} partners, {} users, {} borrowers, {} loans.",
                partners.count(), users.count(), borrowers.count(), loans.count());
    }
}
