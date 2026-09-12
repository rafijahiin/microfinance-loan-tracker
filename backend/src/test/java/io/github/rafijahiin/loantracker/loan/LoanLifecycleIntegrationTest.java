package io.github.rafijahiin.loantracker.loan;

import io.github.rafijahiin.loantracker.borrower.Borrower;
import io.github.rafijahiin.loantracker.partner.PartnerOrganisation;
import io.github.rafijahiin.loantracker.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A loan from disbursement to settlement, through the HTTP layer. */
class LoanLifecycleIntegrationTest extends IntegrationTestBase {

    private PartnerOrganisation po;
    private Borrower member;
    private String token;

    private static final LocalDate DISBURSED = LocalDate.of(2026, 1, 15);

    @BeforeEach
    void setUpPartnerAndMember() throws Exception {
        po = partner("PO-001", "Shomota", "Rangpur");
        officer("officer@example.org", po);
        member = borrower(po, "M-0001", "Rahima Begum");
        token = tokenFor("officer@example.org");
    }

    private String disburseBody(String loanNo, String principal, String rate,
                                int term, LocalDate on) {
        return """
               {"borrowerId":%d,"loanNumber":"%s","principal":%s,
                "annualRate":%s,"termPeriods":%d,"frequency":"MONTHLY",
                "disbursedOn":"%s"}
               """.formatted(member.getId(), loanNo, principal, rate, term, on);
    }

    private Long disburseDefaultLoan() throws Exception {
        // 12,000 at zero interest over 4 months: four instalments of 3,000.
        String body = mvc.perform(post("/api/loans")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(disburseBody("L-0001", "12000", "0.00", 4, DISBURSED)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return asJson(body).get("id").asLong();
    }

    @Test
    @DisplayName("disbursing returns the loan with its generated schedule")
    void disbursementGeneratesTheSchedule() throws Exception {
        mvc.perform(post("/api/loans")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(disburseBody("L-0001", "12000", "0.00", 4, DISBURSED)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.outstanding").value(12000.00))
                .andExpect(jsonPath("$.schedule.length()").value(4))
                .andExpect(jsonPath("$.schedule[0].amountDue").value(3000.00))
                .andExpect(jsonPath("$.schedule[0].dueOn").value("2026-02-15"))
                .andExpect(jsonPath("$.schedule[3].dueOn").value("2026-05-15"));
    }

    @Test
    void aRepaymentReducesTheOutstandingBalance() throws Exception {
        Long loanId = disburseDefaultLoan();

        mvc.perform(post("/api/loans/" + loanId + "/repayments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"receiptNo":"R-001","amount":3000,"receivedOn":"2026-02-15"}
                                 """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(3000.00));

        mvc.perform(get("/api/loans/" + loanId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.totalPaid").value(3000.00))
                .andExpect(jsonPath("$.outstanding").value(9000.00))
                .andExpect(jsonPath("$.schedule[0].status").value("PAID"))
                .andExpect(jsonPath("$.schedule[1].status").value("PENDING"));
    }

    @Test
    @DisplayName("a lump sum settles the oldest instalments first, in order")
    void lumpSumIsAllocatedOldestFirst() throws Exception {
        Long loanId = disburseDefaultLoan();

        // 7,500 covers two instalments of 3,000 and half of the third.
        mvc.perform(post("/api/loans/" + loanId + "/repayments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"receiptNo":"R-002","amount":7500,"receivedOn":"2026-04-15"}
                                 """))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/loans/" + loanId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.schedule[0].status").value("PAID"))
                .andExpect(jsonPath("$.schedule[1].status").value("PAID"))
                .andExpect(jsonPath("$.schedule[2].status").value("PARTIAL"))
                .andExpect(jsonPath("$.schedule[2].amountPaid").value(1500.00))
                .andExpect(jsonPath("$.schedule[3].status").value("PENDING"))
                .andExpect(jsonPath("$.outstanding").value(4500.00));
    }

    @Test
    void settlingEveryInstalmentClosesTheLoan() throws Exception {
        Long loanId = disburseDefaultLoan();

        mvc.perform(post("/api/loans/" + loanId + "/repayments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"receiptNo":"R-003","amount":12000,"receivedOn":"2026-05-15"}
                                 """))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/loans/" + loanId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.outstanding").value(0.00));
    }

    @Test
    @DisplayName("paying more than is owed is refused, not held as a credit")
    void overpaymentIsRefused() throws Exception {
        Long loanId = disburseDefaultLoan();

        mvc.perform(post("/api/loans/" + loanId + "/repayments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"receiptNo":"R-004","amount":12000.01,"receivedOn":"2026-05-15"}
                                 """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("more than")));

        mvc.perform(get("/api/loans/" + loanId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.totalPaid").value(0.00));
    }

    @Test
    void aSettledLoanTakesNoFurtherPayment() throws Exception {
        Long loanId = disburseDefaultLoan();
        mvc.perform(post("/api/loans/" + loanId + "/repayments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"receiptNo":"R-005","amount":12000,"receivedOn":"2026-05-15"}
                                 """))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/loans/" + loanId + "/repayments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"receiptNo":"R-006","amount":100,"receivedOn":"2026-06-15"}
                                 """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("already settled")));
    }

    @Test
    @DisplayName("a receipt number cannot be posted twice")
    void duplicateReceiptIsRefused() throws Exception {
        Long loanId = disburseDefaultLoan();
        String payment = """
                         {"receiptNo":"R-DUP","amount":1000,"receivedOn":"2026-02-15"}
                         """;

        mvc.perform(post("/api/loans/" + loanId + "/repayments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(payment))
                .andExpect(status().isCreated());

        // Without this the same receipt posted twice by a retry would collect
        // the money twice in the ledger.
        mvc.perform(post("/api/loans/" + loanId + "/repayments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(payment))
                .andExpect(status().isConflict());
    }

    @Test
    void aPaymentCannotPredateTheDisbursement() throws Exception {
        Long loanId = disburseDefaultLoan();

        mvc.perform(post("/api/loans/" + loanId + "/repayments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"receiptNo":"R-007","amount":1000,"receivedOn":"2025-12-01"}
                                 """))
                .andExpect(status().isConflict());
    }

    @Test
    void loanNumbersAreUnique() throws Exception {
        disburseDefaultLoan();

        mvc.perform(post("/api/loans")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(disburseBody("L-0001", "5000", "0.10", 6, DISBURSED)))
                .andExpect(status().isConflict());
    }

    @Test
    void aLoanCannotPredateTheMembersEnrolment() throws Exception {
        mvc.perform(post("/api/loans")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(disburseBody("L-0002", "5000", "0.10", 6,
                                member.getEnrolledOn().minusDays(1))))
                .andExpect(status().isConflict());
    }

    @Test
    void malformedDisbursementIsAValidationError() throws Exception {
        mvc.perform(post("/api/loans")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"borrowerId":%d,"loanNumber":"","principal":-5,
                                  "annualRate":2.0,"termPeriods":0,
                                  "frequency":"MONTHLY","disbursedOn":"2026-01-15"}
                                 """.formatted(member.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.loanNumber").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.principal").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.annualRate").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.termPeriods").isNotEmpty());
    }

    @Test
    void anOfficerCannotWriteOffALoan() throws Exception {
        Long loanId = disburseDefaultLoan();

        mvc.perform(post("/api/loans/" + loanId + "/write-off")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAdministratorCanWriteOffAndThenNoPaymentIsAcceptedAgainstTheSchedule()
            throws Exception {
        Long loanId = disburseDefaultLoan();
        admin("admin@example.org");
        String adminToken = tokenFor("admin@example.org");

        mvc.perform(post("/api/loans/" + loanId + "/write-off")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WRITTEN_OFF"));

        mvc.perform(post("/api/loans/" + loanId + "/repayments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"receiptNo":"R-008","amount":500,"receivedOn":"2026-06-01"}
                                 """))
                .andExpect(status().isConflict());
    }

    @Test
    void theRepaymentHistoryIsReturnedInOrder() throws Exception {
        Long loanId = disburseDefaultLoan();
        for (int i = 1; i <= 3; i++) {
            mvc.perform(post("/api/loans/" + loanId + "/repayments")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                     {"receiptNo":"R-1%d","amount":1000,"receivedOn":"2026-0%d-15"}
                                     """.formatted(i, i + 1)))
                    .andExpect(status().isCreated());
        }

        mvc.perform(get("/api/loans/" + loanId + "/repayments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].receiptNo").value("R-11"))
                .andExpect(jsonPath("$[2].receiptNo").value("R-13"));
    }
}
