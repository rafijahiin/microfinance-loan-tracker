package bd.org.pksf.loantracker;

import bd.org.pksf.loantracker.partner.PartnerOrganisation;
import bd.org.pksf.loantracker.partner.PartnerRepository;
import bd.org.pksf.loantracker.user.AppUser;
import bd.org.pksf.loantracker.user.AppUserRepository;
import bd.org.pksf.loantracker.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the application on a real port and talks to it over TCP.
 *
 * The rest of the integration suite uses MockMvc, which exercises the Spring
 * MVC stack but not a servlet container, a real socket, or the full
 * serialisation path. This one does, so a failure that only appears once an
 * actual HTTP server is involved has somewhere to show up.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SmokeTest {

    @LocalServerPort
    private int port;

    @Autowired private TestRestTemplate rest;
    @Autowired private bd.org.pksf.loantracker.support.DatabaseCleaner databaseCleaner;
    @Autowired private PartnerRepository partners;
    @Autowired private AppUserRepository users;
    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder encoder;

    private String base() {
        return "http://localhost:" + port;
    }

    @BeforeEach
    void seedOneOfficer() {
        // Deleting only users and partners left borrowers and loans from other
        // test classes pointing at rows that were about to vanish. H2 tolerated
        // it; PostgreSQL refused, correctly.
        databaseCleaner.clean();
        PartnerOrganisation po = partners.save(
                new PartnerOrganisation("PO-001", "Shomota", "Rangpur"));
        users.save(new AppUser("officer@example.org",
                encoder.encode("smoke-test-password"), Role.PO_OFFICER, po));
    }

    @Test
    @DisplayName("the application starts and answers its health check")
    void healthEndpointIsUpAndUnauthenticated() {
        ResponseEntity<String> res =
                rest.getForEntity(base() + "/actuator/health", String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("UP");
    }

    @Test
    @DisplayName("the OpenAPI document is served, so the published contract is real")
    void openApiDocumentIsGenerated() {
        ResponseEntity<String> res =
                rest.getForEntity(base() + "/v3/api-docs", String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody())
                .contains("/api/loans/{id}/repayments")
                .contains("/api/portfolio/summary")
                .contains("bearer-jwt");
    }

    @Test
    void signingInOverRealHttpReturnsAUsableToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> login = rest.postForEntity(
                base() + "/api/auth/login",
                new HttpEntity<>("""
                                 {"email":"officer@example.org",
                                  "password":"smoke-test-password"}
                                 """, headers),
                String.class);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.CREATED.OK);
        assertThat(login.getBody()).contains("\"tokenType\":\"Bearer\"");
    }

    @Test
    void anUnauthenticatedCallIsRefusedWithTheStandardErrorShape() {
        ResponseEntity<String> res =
                rest.getForEntity(base() + "/api/loans", String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(res.getBody())
                .contains("\"status\":401")
                .contains("\"error\":\"Unauthorized\"");
    }
}
