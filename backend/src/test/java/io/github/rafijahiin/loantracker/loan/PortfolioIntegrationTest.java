package io.github.rafijahiin.loantracker.loan;

import io.github.rafijahiin.loantracker.borrower.Borrower;
import io.github.rafijahiin.loantracker.partner.PartnerOrganisation;
import io.github.rafijahiin.loantracker.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Portfolio at risk, read on a fixed date so the numbers are checkable by hand.
 *
 * The portfolio is built to have three distinct shapes: a loan badly in
 * arrears, a loan only slightly late, and a loan not yet due. PAR30 must pick
 * up only the first.
 */
class PortfolioIntegrationTest extends IntegrationTestBase {

    @Autowired
    private ScheduleGenerator scheduleGenerator;

    private static final LocalDate AS_OF = LocalDate.of(2026, 6, 15);

    private PartnerOrganisation rangpur;

    @BeforeEach
    void buildAPortfolio() {
        rangpur = partner("PO-001", "Shomota", "Rangpur");
        PartnerOrganisation barishal = partner("PO-002", "Nodi Jonopod", "Barishal");
        officer("rangpur@example.org", rangpur);
        officer("barishal@example.org", barishal);
        admin("admin@example.org");

        // Badly late: four instalments of 3,000 from 15 Feb, none paid. The
        // oldest is 120 days overdue at the reading date.
        newLoan(rangpur, "M-0001", "L-A", "12000", 4, LocalDate.of(2026, 1, 15));

        // Slightly late: 1,250 a month from 25 May, none paid. Twenty-one days
        // overdue, which is arrears but not PAR30.
        newLoan(rangpur, "M-0002", "L-B", "5000", 4, LocalDate.of(2026, 4, 25));

        // Not yet due: first instalment falls on 20 June, after the reading.
        newLoan(rangpur, "M-0003", "L-C", "8000", 4, LocalDate.of(2026, 5, 20));

        // Another partner's loan, which must not appear in Rangpur's figures.
        newLoan(barishal, "M-0001", "L-D", "40000", 4, LocalDate.of(2026, 1, 15));
    }

    private void newLoan(PartnerOrganisation po, String memberCode, String loanNo,
                         String principal, int term, LocalDate disbursedOn) {
        Borrower b = borrowers.save(new Borrower(po, memberCode,
                "Member " + memberCode, po.getDistrict(), disbursedOn.minusMonths(1)));
        Loan loan = new Loan(loanNo, b, new BigDecimal(principal), BigDecimal.ZERO,
                term, RepaymentFrequency.MONTHLY, disbursedOn);
        scheduleGenerator.generate(loan.getPrincipal(), BigDecimal.ZERO, term,
                        RepaymentFrequency.MONTHLY, disbursedOn)
                .forEach(loan::addInstalment);
        loans.save(loan);
    }

    @Test
    @DisplayName("an officer's summary covers only their own partner")
    void summaryIsScopedToTheCallersPartner() throws Exception {
        // 12,000 + 5,000 + 8,000. The Barishal loan is excluded.
        mvc.perform(get("/api/portfolio/summary")
                        .param("asOf", AS_OF.toString())
                        .header(HttpHeaders.AUTHORIZATION,
                                bearer(tokenFor("rangpur@example.org"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeLoans").value(3))
                .andExpect(jsonPath("$.outstanding").value(25000.00));
    }

    @Test
    @DisplayName("overdue counts only instalments already past due")
    void overdueIsNotTheSameAsOutstanding() throws Exception {
        // L-A has all four instalments past due (12,000). L-B has one (1,250).
        // L-C has none.
        mvc.perform(get("/api/portfolio/summary")
                        .param("asOf", AS_OF.toString())
                        .header(HttpHeaders.AUTHORIZATION,
                                bearer(tokenFor("rangpur@example.org"))))
                .andExpect(jsonPath("$.overdue").value(13250.00))
                .andExpect(jsonPath("$.loansInArrears").value(2));
    }

    @Test
    @DisplayName("PAR30 takes the whole balance of a loan over 30 days late, and only those")
    void par30CountsTheFullBalanceOfBadlyLateLoans() throws Exception {
        // L-A alone is more than 30 days down, so 12,000 of 25,000 is at risk.
        // L-B is late but only by 21 days, so none of its balance counts.
        mvc.perform(get("/api/portfolio/summary")
                        .param("asOf", AS_OF.toString())
                        .header(HttpHeaders.AUTHORIZATION,
                                bearer(tokenFor("rangpur@example.org"))))
                .andExpect(jsonPath("$.par30").value(0.48));
    }

    @Test
    void anAdministratorSeesEveryPartnerInOneFigure() throws Exception {
        // 25,000 from Rangpur plus 40,000 from Barishal.
        mvc.perform(get("/api/portfolio/summary")
                        .param("asOf", AS_OF.toString())
                        .header(HttpHeaders.AUTHORIZATION,
                                bearer(tokenFor("admin@example.org"))))
                .andExpect(jsonPath("$.activeLoans").value(4))
                .andExpect(jsonPath("$.outstanding").value(65000.00));
    }

    @Test
    @DisplayName("a portfolio with nothing in it reports zero, not a divide by zero")
    void anEmptyPortfolioDoesNotBlowUp() throws Exception {
        loans.deleteAll();

        mvc.perform(get("/api/portfolio/summary")
                        .param("asOf", AS_OF.toString())
                        .header(HttpHeaders.AUTHORIZATION,
                                bearer(tokenFor("rangpur@example.org"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeLoans").value(0))
                .andExpect(jsonPath("$.par30").value(0));
    }

    @Test
    void theLoanListingIsAlsoScopedAndPaged() throws Exception {
        mvc.perform(get("/api/loans")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION,
                                bearer(tokenFor("rangpur@example.org"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content.length()").value(2))
                // The summary view carries balances but not the schedule.
                .andExpect(jsonPath("$.content[0].outstanding").isNumber())
                .andExpect(jsonPath("$.content[0].schedule").doesNotExist());
    }
}
