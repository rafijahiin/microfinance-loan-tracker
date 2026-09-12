package io.github.rafijahiin.loantracker.security;

import io.github.rafijahiin.loantracker.partner.PartnerOrganisation;
import io.github.rafijahiin.loantracker.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends IntegrationTestBase {

    @Test
    void validCredentialsReturnAToken() throws Exception {
        admin("admin@example.org");

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"email":"admin@example.org","password":"%s"}
                                 """.formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.partnerId").doesNotExist());
    }

    @Test
    @DisplayName("an officer's token carries their partner id")
    void officerTokenCarriesPartnerScope() throws Exception {
        PartnerOrganisation po = partner("PO-001", "Shomota", "Rangpur");
        officer("officer@example.org", po);

        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"email":"officer@example.org","password":"%s"}
                                 """.formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PO_OFFICER"))
                .andReturn().getResponse().getContentAsString();

        assertThat(asJson(body).get("partnerId").asLong()).isEqualTo(po.getId());
    }

    @Test
    @DisplayName("a wrong password and an unknown account answer identically")
    void loginDoesNotRevealWhetherAnAccountExists() throws Exception {
        admin("admin@example.org");

        String wrongPassword = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"email":"admin@example.org","password":"not-it"}
                                 """))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String unknownUser = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"email":"nobody@example.org","password":"not-it"}
                                 """))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        // Differing messages would turn this endpoint into a way to find out
        // who holds an account.
        assertThat(asJson(wrongPassword).get("message").asText())
                .isEqualTo(asJson(unknownUser).get("message").asText());
    }

    @Test
    void aDisabledAccountCannotLogIn() throws Exception {
        var user = admin("admin@example.org");
        user.setEnabled(false);
        users.save(user);

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"email":"admin@example.org","password":"%s"}
                                 """.formatted(PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointsRefuseAnAnonymousCaller() throws Exception {
        mvc.perform(get("/api/partners"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("a tampered token is rejected, not trusted")
    void aTamperedTokenIsRejected() throws Exception {
        admin("admin@example.org");
        String token = tokenFor("admin@example.org");

        // Flip the last character of the signature.
        char last = token.charAt(token.length() - 1);
        String forged = token.substring(0, token.length() - 1)
                + (last == 'A' ? 'B' : 'A');

        mvc.perform(get("/api/partners").header(HttpHeaders.AUTHORIZATION, bearer(forged)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meReportsTheCallerTheServerResolved() throws Exception {
        PartnerOrganisation po = partner("PO-001", "Shomota", "Rangpur");
        officer("officer@example.org", po);

        mvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION,
                                bearer(tokenFor("officer@example.org"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("officer@example.org"))
                .andExpect(jsonPath("$.role").value("PO_OFFICER"))
                .andExpect(jsonPath("$.partnerId").value(po.getId()));
    }

    @Test
    void malformedLoginPayloadIsAValidationError() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"email":"not-an-email","password":""}
                                 """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.password").isNotEmpty());
    }
}
