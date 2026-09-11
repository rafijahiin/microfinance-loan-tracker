package bd.org.pksf.loantracker.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Signing key and lifetime, supplied by configuration.
 *
 *  There is no default secret here on purpose. A committed fallback key is the
 *  most common way a service ends up running in production with a signing key
 *  that is public on GitHub.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, long expiryMinutes) {

    public JwtProperties {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret must be set and at least 32 characters. "
                    + "HMAC-SHA256 needs a key of at least the digest length; "
                    + "a shorter one weakens every token the service issues.");
        }
        if (expiryMinutes <= 0) {
            throw new IllegalStateException("app.jwt.expiry-minutes must be positive");
        }
    }
}
