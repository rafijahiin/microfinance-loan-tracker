package io.github.rafijahiin.loantracker.borrower;

import io.github.rafijahiin.loantracker.partner.PartnerOrganisation;
import io.github.rafijahiin.loantracker.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The national ID, end to end: never stored, never returned, never duplicated. */
class BorrowerNationalIdIntegrationTest extends IntegrationTestBase {

    private static final String NID = "1990123456789";

    private PartnerOrganisation rangpur;
    private String token;

    @BeforeEach
    void setUpPartner() throws Exception {
        rangpur = partner("PO-001", "Shomota", "Rangpur");
        officer("officer@example.org", rangpur);
        token = tokenFor("officer@example.org");
    }

    private String enrolBody(String memberCode, String nid) {
        return """
               {"partnerId":%d,"memberCode":"%s","name":"Rahima Begum",
                "nationalId":"%s","district":"Rangpur","enrolledOn":"2026-01-05"}
               """.formatted(rangpur.getId(), memberCode, nid);
    }

    @Test
    @DisplayName("enrolment returns the masked number and never the raw one")
    void theApiReturnsOnlyTheMask() throws Exception {
        String body = mvc.perform(post("/api/borrowers")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(enrolBody("M-0001", NID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nationalIdMasked").value("*********6789"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(NID);
        // The hash is an internal detail. Returning it would hand an attacker
        // the value to match a stolen table against.
        assertThat(body).doesNotContain("nationalIdHash");
    }

    @Test
    @DisplayName("the raw number is not in the database, only its hash")
    void theRawNumberIsNeverPersisted() throws Exception {
        mvc.perform(post("/api/borrowers")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(enrolBody("M-0001", NID)))
                .andExpect(status().isCreated());

        Borrower saved = borrowers.findAll().get(0);
        assertThat(saved.getNationalIdHash()).hasSize(64).doesNotContain(NID);
        assertThat(saved.getNationalIdMasked()).isEqualTo("*********6789");
    }

    @Test
    @DisplayName("the same person cannot be enrolled twice under one partner")
    void duplicateNationalIdIsRefused() throws Exception {
        mvc.perform(post("/api/borrowers")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(enrolBody("M-0001", NID)))
                .andExpect(status().isCreated());

        // Different member code, same person. A duplicate enrolment inflates
        // the outreach figures the programme is judged on.
        mvc.perform(post("/api/borrowers")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(enrolBody("M-0002", NID)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("already enrolled")));

        assertThat(borrowers.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("the duplicate message does not echo the number back")
    void theDuplicateMessageIsNotAMembershipOracle() throws Exception {
        mvc.perform(post("/api/borrowers")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(enrolBody("M-0001", NID)))
                .andExpect(status().isCreated());

        String body = mvc.perform(post("/api/borrowers")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(enrolBody("M-0002", NID)))
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(NID);
    }

    @Test
    @DisplayName("differently formatted entries of one number still collide")
    void formattingDoesNotDefeatTheDuplicateCheck() throws Exception {
        mvc.perform(post("/api/borrowers")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(enrolBody("M-0001", NID)))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/borrowers")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(enrolBody("M-0002", "1990 1234 56789")))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("two partners may each enrol the same person")
    void theDuplicateRuleIsScopedToOnePartner() throws Exception {
        PartnerOrganisation barishal = partner("PO-002", "Nodi Jonopod", "Barishal");
        officer("barishal@example.org", barishal);

        mvc.perform(post("/api/borrowers")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(enrolBody("M-0001", NID)))
                .andExpect(status().isCreated());

        // A woman can genuinely be a member of two organisations. Making this
        // globally unique would block a legitimate second enrolment, and would
        // also leak, through the rejection, that she is a member elsewhere.
        mvc.perform(post("/api/borrowers")
                        .header(HttpHeaders.AUTHORIZATION,
                                bearer(tokenFor("barishal@example.org")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"partnerId":%d,"memberCode":"M-0001",
                                  "name":"Rahima Begum","nationalId":"%s",
                                  "district":"Barishal","enrolledOn":"2026-01-05"}
                                 """.formatted(barishal.getId(), NID)))
                .andExpect(status().isCreated());
    }

    @Test
    void aMalformedNationalIdIsRejected() throws Exception {
        mvc.perform(post("/api/borrowers")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(enrolBody("M-0001", "not-a-number")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("digits")));
    }

    @Test
    void aMissingNationalIdIsAValidationError() throws Exception {
        mvc.perform(post("/api/borrowers")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"partnerId":%d,"memberCode":"M-0001",
                                  "name":"Rahima Begum","district":"Rangpur",
                                  "enrolledOn":"2026-01-05"}
                                 """.formatted(rangpur.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.nationalId").isNotEmpty());
    }

    @Test
    void theListingAlsoShowsOnlyTheMask() throws Exception {
        mvc.perform(post("/api/borrowers")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(enrolBody("M-0001", NID)))
                .andExpect(status().isCreated());

        String body = mvc.perform(get("/api/borrowers")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].nationalIdMasked")
                        .value("*********6789"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(NID);
    }
}
