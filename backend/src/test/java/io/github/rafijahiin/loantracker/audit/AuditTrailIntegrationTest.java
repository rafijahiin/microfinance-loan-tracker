package io.github.rafijahiin.loantracker.audit;

import io.github.rafijahiin.loantracker.partner.PartnerOrganisation;
import io.github.rafijahiin.loantracker.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The trail answers "who did this, and when".
 *
 * `created_at` and `updated_at` say when a row last changed. They do not say
 * who changed it, and after an update they no longer say what it said before.
 * On a table of financial records that is the question that actually gets
 * asked, months later, when a member disputes a receipt.
 */
class AuditTrailIntegrationTest extends IntegrationTestBase {

    @Autowired private AuditEventRepository auditEvents;
    @Autowired private AuditService auditService;

    private static final String NID = "1990123456789";

    private PartnerOrganisation rangpur;
    private String officerToken;

    @BeforeEach
    void setUpPartner() throws Exception {
        rangpur = partner("PO-001", "Shomota", "Rangpur");
        officer("officer.rangpur@example.org", rangpur);
        admin("admin@example.org");
        officerToken = tokenFor("officer.rangpur@example.org");
    }

    private Long enrolMember(String token, Long partnerId, String code, String nid)
            throws Exception {
        String body = mvc.perform(post("/api/borrowers")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"partnerId":%d,"memberCode":"%s","name":"Rahima Begum",
                                  "nationalId":"%s","district":"Rangpur",
                                  "enrolledOn":"2026-01-05"}
                                 """.formatted(partnerId, code, nid)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return asJson(body).get("id").asLong();
    }

    private Long disburseLoan(String token, Long borrowerId, String loanNo)
            throws Exception {
        String body = mvc.perform(post("/api/loans")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"borrowerId":%d,"loanNumber":"%s","principal":12000,
                                  "annualRate":0.00,"termPeriods":4,
                                  "frequency":"MONTHLY","disbursedOn":"2026-01-10"}
                                 """.formatted(borrowerId, loanNo)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return asJson(body).get("id").asLong();
    }

    @Test
    @DisplayName("every write records who did it")
    void theActorIsRecordedOnEveryAction() throws Exception {
        Long borrowerId = enrolMember(officerToken, rangpur.getId(), "M-0001", NID);
        Long loanId = disburseLoan(officerToken, borrowerId, "L-0001");

        mvc.perform(post("/api/loans/" + loanId + "/repayments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(officerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"receiptNo":"R-001","amount":3000,"receivedOn":"2026-02-10"}
                                 """))
                .andExpect(status().isCreated());

        List<AuditEvent> all = auditEvents.findAll();
        assertThat(all).hasSize(3);
        assertThat(all).allSatisfy(e -> {
            assertThat(e.getActorEmail()).isEqualTo("officer.rangpur@example.org");
            assertThat(e.getActorRole()).isEqualTo("PO_OFFICER");
            assertThat(e.getOccurredAt()).isNotNull();
        });
        assertThat(all).extracting(AuditEvent::getAction)
                .containsExactlyInAnyOrder(AuditAction.MEMBER_ENROLLED,
                        AuditAction.LOAN_DISBURSED, AuditAction.REPAYMENT_POSTED);
    }

    @Test
    @DisplayName("the trail never contains the national ID that was typed")
    void theTrailCarriesTheMaskedNumberOnly() throws Exception {
        enrolMember(officerToken, rangpur.getId(), "M-0001", NID);

        AuditEvent entry = auditEvents.findAll().get(0);

        // An audit table is a long-lived, widely-read copy of whatever goes
        // into it. If a national ID reaches it, every later reader of the trail
        // has it too, and the hashing upstream was pointless.
        assertThat(entry.getSummary()).doesNotContain(NID);
        assertThat(entry.getSummary()).contains("*********6789");
    }

    @Test
    @DisplayName("a repayment entry carries the amount and the balance it left")
    void aRepaymentEntryIsReadableWithoutTheSchema() throws Exception {
        Long borrowerId = enrolMember(officerToken, rangpur.getId(), "M-0001", NID);
        Long loanId = disburseLoan(officerToken, borrowerId, "L-0001");

        mvc.perform(post("/api/loans/" + loanId + "/repayments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(officerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"receiptNo":"R-001","amount":3000,"receivedOn":"2026-02-10"}
                                 """))
                .andExpect(status().isCreated());

        AuditEvent entry = auditEvents.findAll().stream()
                .filter(e -> e.getAction() == AuditAction.REPAYMENT_POSTED)
                .findFirst().orElseThrow();

        assertThat(entry.getAmount()).isEqualByComparingTo("3000.00");
        assertThat(entry.getSummary())
                .contains("R-001")
                .contains("9000.00");
    }

    @Test
    @DisplayName("a write-off records the balance as it stood on the day")
    void aWriteOffCapturesWhatWasGivenUp() throws Exception {
        Long borrowerId = enrolMember(officerToken, rangpur.getId(), "M-0001", NID);
        Long loanId = disburseLoan(officerToken, borrowerId, "L-0001");

        mvc.perform(post("/api/loans/" + loanId + "/write-off")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor("admin@example.org"))))
                .andExpect(status().isOk());

        AuditEvent entry = auditEvents.findAll().stream()
                .filter(e -> e.getAction() == AuditAction.LOAN_WRITTEN_OFF)
                .findFirst().orElseThrow();

        // Captured at the decision, not read back later: the schedule can be
        // changed afterwards, the amount given up on the day cannot.
        assertThat(entry.getAmount()).isEqualByComparingTo("12000.00");
        assertThat(entry.getActorRole()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("a refused change leaves no entry claiming it happened")
    void aRejectedWriteIsNotRecorded() throws Exception {
        Long borrowerId = enrolMember(officerToken, rangpur.getId(), "M-0001", NID);
        Long loanId = disburseLoan(officerToken, borrowerId, "L-0001");
        long before = auditEvents.count();

        mvc.perform(post("/api/loans/" + loanId + "/repayments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(officerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"receiptNo":"R-BAD","amount":99999,"receivedOn":"2026-02-10"}
                                 """))
                .andExpect(status().isConflict());

        // A trail that records attempts as though they succeeded is worse than
        // no trail, because it would be believed.
        assertThat(auditEvents.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("an entry cannot be written outside the transaction it describes")
    void recordingRequiresAnEnclosingTransaction() {
        // Propagation.MANDATORY. If the change rolls back, the entry must roll
        // back with it, which is only true when the two share a transaction.
        assertThatThrownBy(() -> auditService.record(
                new io.github.rafijahiin.loantracker.security.AuthenticatedUser(
                        1L, "someone@example.org",
                        io.github.rafijahiin.loantracker.user.Role.ADMIN, null),
                AuditAction.LOAN_DISBURSED, null, AuditService.ENTITY_LOAN, 1L,
                "should never be written", null))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);

        assertThat(auditEvents.count()).isZero();
    }

    @Test
    @DisplayName("an officer sees only their own partner's activity")
    void theTrailIsScopedLikeEverythingElse() throws Exception {
        PartnerOrganisation barishal = partner("PO-002", "Nodi Jonopod", "Barishal");
        officer("officer.barishal@example.org", barishal);
        String barishalToken = tokenFor("officer.barishal@example.org");

        enrolMember(officerToken, rangpur.getId(), "M-0001", NID);
        enrolMember(barishalToken, barishal.getId(), "M-0001", "1991222333444");

        // Two entries exist; each officer sees exactly one of them. Leaving the
        // trail unscoped would make it the one place an officer could learn
        // about another organisation's lending.
        mvc.perform(get("/api/audit")
                        .header(HttpHeaders.AUTHORIZATION, bearer(officerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].actorEmail")
                        .value("officer.rangpur@example.org"));

        mvc.perform(get("/api/audit")
                        .header(HttpHeaders.AUTHORIZATION, bearer(barishalToken)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].actorEmail")
                        .value("officer.barishal@example.org"));

        mvc.perform(get("/api/audit")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor("admin@example.org"))))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("a loan's own history reads newest first")
    void aLoanCarriesItsOwnHistory() throws Exception {
        Long borrowerId = enrolMember(officerToken, rangpur.getId(), "M-0001", NID);
        Long loanId = disburseLoan(officerToken, borrowerId, "L-0001");

        for (int i = 1; i <= 2; i++) {
            mvc.perform(post("/api/loans/" + loanId + "/repayments")
                            .header(HttpHeaders.AUTHORIZATION, bearer(officerToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                     {"receiptNo":"R-00%d","amount":3000,"receivedOn":"2026-0%d-10"}
                                     """.formatted(i, i + 1)))
                    .andExpect(status().isCreated());
        }

        mvc.perform(get("/api/loans/" + loanId + "/audit")
                        .header(HttpHeaders.AUTHORIZATION, bearer(officerToken)))
                .andExpect(status().isOk())
                // Disbursement plus two repayments. The enrolment belongs to
                // the borrower, not this loan, so it is not here.
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].action").value("REPAYMENT_POSTED"))
                .andExpect(jsonPath("$[2].action").value("LOAN_DISBURSED"));
    }

    @Test
    @DisplayName("another partner's loan history is a 404, not a peek")
    void aLoansHistoryIsNotAWayAroundScoping() throws Exception {
        PartnerOrganisation barishal = partner("PO-002", "Nodi Jonopod", "Barishal");
        officer("officer.barishal@example.org", barishal);

        Long borrowerId = enrolMember(officerToken, rangpur.getId(), "M-0001", NID);
        Long loanId = disburseLoan(officerToken, borrowerId, "L-0001");

        mvc.perform(get("/api/loans/" + loanId + "/audit")
                        .header(HttpHeaders.AUTHORIZATION,
                                bearer(tokenFor("officer.barishal@example.org"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("the trail is append-only: there is no way in over HTTP")
    void theApiOffersNoWayToWriteOrEraseTheTrail() throws Exception {
        enrolMember(officerToken, rangpur.getId(), "M-0001", NID);
        Long entryId = auditEvents.findAll().get(0).getId();

        // Entries are written by the services that make the changes, inside the
        // same transaction. Nothing should be able to add or remove one from
        // outside, or the trail proves nothing.
        mvc.perform(post("/api/audit")
                        .header(HttpHeaders.AUTHORIZATION, bearer(officerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is4xxClientError());

        mvc.perform(delete("/api/audit/" + entryId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(officerToken)))
                .andExpect(status().is4xxClientError());

        assertThat(auditEvents.count()).isEqualTo(1);
    }
}
