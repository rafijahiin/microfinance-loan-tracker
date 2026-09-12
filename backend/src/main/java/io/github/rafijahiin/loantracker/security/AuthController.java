package io.github.rafijahiin.loantracker.security;

import io.github.rafijahiin.loantracker.user.AppUser;
import io.github.rafijahiin.loantracker.user.AppUserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final CurrentUser currentUser;

    public AuthController(AppUserRepository users, PasswordEncoder encoder,
                          JwtService jwt, CurrentUser currentUser) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.currentUser = currentUser;
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {
    }

    public record LoginResponse(String token, String tokenType, long expiresInSeconds,
                                String email, String role, Long partnerId) {
    }

    @PostMapping("/login")
    @Operation(summary = "Exchange credentials for a bearer token")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        Optional<AppUser> found = users.findByEmailWithPartner(req.email());

        // One message and one status for "no such user", "wrong password" and
        // "account disabled". Distinguishing them turns the login endpoint into
        // a way to enumerate who holds an account.
        if (found.isEmpty()
                || !found.get().isEnabled()
                || !encoder.matches(req.password(), found.get().getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    io.github.rafijahiin.loantracker.common.ApiError.of(
                            401, "Unauthorized", "Invalid email or password"));
        }

        AppUser user = found.get();
        return ResponseEntity.ok(new LoginResponse(
                jwt.issue(user), "Bearer", jwt.getExpirySeconds(),
                user.getEmail(), user.getRole().name(), user.getPartnerId()));
    }

    @GetMapping("/me")
    @Operation(summary = "The caller as the server understands them")
    public AuthenticatedUser me() {
        return currentUser.get();
    }
}
