package bd.org.pksf.loantracker.support;

import bd.org.pksf.loantracker.borrower.Borrower;
import bd.org.pksf.loantracker.borrower.BorrowerRepository;
import bd.org.pksf.loantracker.loan.LoanRepository;
import bd.org.pksf.loantracker.loan.RepaymentRepository;
import bd.org.pksf.loantracker.partner.PartnerOrganisation;
import bd.org.pksf.loantracker.partner.PartnerRepository;
import bd.org.pksf.loantracker.user.AppUser;
import bd.org.pksf.loantracker.user.AppUserRepository;
import bd.org.pksf.loantracker.user.Role;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected PartnerRepository partners;
    @Autowired protected AppUserRepository users;
    @Autowired protected BorrowerRepository borrowers;
    @Autowired protected LoanRepository loans;
    @Autowired protected RepaymentRepository repayments;
    @Autowired protected PasswordEncoder encoder;

    protected static final String PASSWORD = "correct-horse-battery";

    @Autowired protected DatabaseCleaner databaseCleaner;

    /** Wiped before each test. Shared state between tests is how a suite starts
     *  passing only in the order it was written. The order lives in
     *  DatabaseCleaner so no two test classes can disagree about it. */
    @BeforeEach
    void resetDatabase() {
        databaseCleaner.clean();
    }

    protected PartnerOrganisation partner(String code, String name, String district) {
        return partners.save(new PartnerOrganisation(code, name, district));
    }

    protected AppUser admin(String email) {
        return users.save(new AppUser(email, encoder.encode(PASSWORD), Role.ADMIN, null));
    }

    protected AppUser officer(String email, PartnerOrganisation po) {
        return users.save(new AppUser(email, encoder.encode(PASSWORD),
                Role.PO_OFFICER, po));
    }

    protected Borrower borrower(PartnerOrganisation po, String code, String name) {
        return borrowers.save(new Borrower(po, code, name, po.getDistrict(),
                LocalDate.now().minusYears(1)));
    }

    /** Logs in through the real endpoint rather than forging a token, so every
     *  test also exercises the authentication path it depends on. */
    protected String tokenFor(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"email":"%s","password":"%s"}
                                 """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("token").asText();
    }

    protected String bearer(String token) {
        return "Bearer " + token;
    }

    protected JsonNode asJson(String body) throws Exception {
        return json.readTree(body);
    }
}
