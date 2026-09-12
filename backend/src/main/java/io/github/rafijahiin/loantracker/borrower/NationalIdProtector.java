package io.github.rafijahiin.loantracker.borrower;

import io.github.rafijahiin.loantracker.common.BusinessRuleException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Handles the national ID number without ever storing it.
 *
 * A member's NID is the most sensitive field in this system. It is a lifelong
 * government identifier, it is reused across every service she touches, and a
 * leaked lender database full of them is materially worse than one full of
 * names. So the number itself is never persisted. Two derived values are:
 *
 *   - a keyed hash, which is what duplicate detection compares, and
 *   - the last four digits, which is what staff see when confirming identity.
 *
 * HMAC-SHA256 with a secret pepper rather than a bare digest. A bare
 * SHA-256 of a national ID is not protection: the format is short and
 * structured enough that the whole space can be enumerated and matched against
 * a stolen table in minutes. The pepper is not in the database, so stealing the
 * table alone gives an attacker nothing to match against.
 *
 * The pepper is required and has no default, for the same reason the JWT
 * signing key has none. A committed pepper is a published pepper, and every
 * hash computed under it is back to being enumerable.
 */
@Service
public class NationalIdProtector {

    private static final String ALGORITHM = "HmacSHA256";
    private static final int MIN_PEPPER_LENGTH = 16;
    private static final int VISIBLE_DIGITS = 4;

    private final byte[] pepper;

    public NationalIdProtector(@Value("${app.nid.pepper:}") String pepper) {
        if (pepper == null || pepper.length() < MIN_PEPPER_LENGTH) {
            throw new IllegalStateException(
                    "app.nid.pepper must be set and at least " + MIN_PEPPER_LENGTH
                    + " characters. Without it, national ID hashes are "
                    + "enumerable and the protection is decorative.");
        }
        this.pepper = pepper.getBytes(StandardCharsets.UTF_8);
    }

    /** Hex-encoded HMAC of the normalised number. Deterministic, so the same
     *  NID always produces the same hash and duplicates can be found without
     *  the original ever being stored. */
    public String hash(String nationalId) {
        String normalised = normalise(nationalId);
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(pepper, ALGORITHM));
            return HexFormat.of().formatHex(
                    mac.doFinal(normalised.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            // Both are configuration faults, not runtime conditions: every JVM
            // ships HmacSHA256, and the key was validated in the constructor.
            throw new IllegalStateException("Cannot compute the national ID hash", ex);
        }
    }

    /** The last four digits, masked to the original length, for staff to
     *  confirm they have the right woman without exposing the number. */
    public String mask(String nationalId) {
        String normalised = normalise(nationalId);
        if (normalised.length() <= VISIBLE_DIGITS) {
            return "*".repeat(normalised.length());
        }
        return "*".repeat(normalised.length() - VISIBLE_DIGITS)
                + normalised.substring(normalised.length() - VISIBLE_DIGITS);
    }

    /** Strips spaces and dashes so that "1990 1234 56789" and "199012345678 9"
     *  hash alike. Without this the same person enrolled twice, typed slightly
     *  differently, produces two different hashes and the duplicate check
     *  silently passes. */
    private String normalise(String nationalId) {
        String cleaned = nationalId == null ? "" : nationalId.replaceAll("[\\s-]", "");
        if (cleaned.isEmpty()) {
            throw new BusinessRuleException("A national ID number is required");
        }
        if (!cleaned.chars().allMatch(Character::isDigit)) {
            throw new BusinessRuleException(
                    "A national ID number must contain digits only");
        }
        return cleaned;
    }
}
