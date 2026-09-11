package bd.org.pksf.loantracker.security;

import bd.org.pksf.loantracker.borrower.Borrower;
import bd.org.pksf.loantracker.partner.PartnerOrganisation;
import bd.org.pksf.loantracker.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The rule the whole authorisation model rests on: a partner organisation sees
 * its own members and nobody else's.
 *
 * These are the tests worth writing first. A mistake here does not throw an
 * error, it quietly shows one lender another lender's borrower list.
 */
class PartnerScopingIntegrationTest extends IntegrationTestBase {

    private PartnerOrganisation rangpur;
    private PartnerOrganisation barishal;
    private Borrower rangpurMember;
    private Borrower barishalMember;

    @BeforeEach
    void setUpTwoPartners() {
        rangpur = partner("PO-001", "Shomota", "Rangpur");
        barishal = partner("PO-002", "Nodi Jonopod", "Barishal");

        officer("rangpur@example.org", rangpur);
        officer("barishal@example.org", barishal);
        admin("admin@example.org");

        rangpurMember = borrower(rangpur, "M-0001", "Rahima Begum");
        barishalMember = borrower(barishal, "M-0001", "Jahanara Khatun");
    }

    @Test
    void anOfficerSeesOnlyTheirOwnMembersInAListing() throws Exception {
        mvc.perform(get("/api/borrowers")
                        .header(HttpHeaders.AUTHORIZATION,
                                bearer(tokenFor("rangpur@example.org"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Rahima Begum"));
    }

    @Test
    @DisplayName("reading another partner's member answers 404, not 403")
    void crossPartnerReadIsIndistinguishableFromNotExisting() throws Exception {
        // 403 would confirm the record exists. Walking the id space would then
        // reveal how many members another partner has.
        mvc.perform(get("/api/borrowers/" + barishalMember.getId())
                        .header(HttpHeaders.AUTHORIZATION,
                                bearer(tokenFor("rangpur@example.org"))))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/borrowers/999999")
                        .header(HttpHeaders.AUTHORIZATION,
                                bearer(tokenFor("rangpur@example.org"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void anOfficerCannotEnrolAMemberUnderAnotherPartner() throws Exception {
        mvc.perform(post("/api/borrowers")
                        .header(HttpHeaders.AUTHORIZATION,
                                bearer(tokenFor("rangpur@example.org")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"partnerId":%d,"memberCode":"M-9999",
                                  "name":"Planted Member","district":"Barishal",
                                  "enrolledOn":"2026-01-01"}
                                 """.formatted(barishal.getId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void anOfficerSeesOnlyTheirOwnOrganisationInThePartnerList() throws Exception {
        mvc.perform(get("/api/partners")
                        .header(HttpHeaders.AUTHORIZATION,
                                bearer(tokenFor("barishal@example.org"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].code").value("PO-002"));
    }

    @Test
    void anAdministratorSeesEveryPartnerAndEveryMember() throws Exception {
        String token = tokenFor("admin@example.org");

        mvc.perform(get("/api/partners").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mvc.perform(get("/api/borrowers").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mvc.perform(get("/api/borrowers/" + rangpurMember.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    void writingOffALoanIsRefusedToAnOfficer() throws Exception {
        mvc.perform(post("/api/partners")
                        .header(HttpHeaders.AUTHORIZATION,
                                bearer(tokenFor("rangpur@example.org")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"code":"PO-003","name":"Sneaky","district":"Dhaka"}
                                 """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }
}
