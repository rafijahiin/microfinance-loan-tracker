package io.github.rafijahiin.loantracker.borrower;

import io.github.rafijahiin.loantracker.common.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NationalIdProtectorTest {

    private static final String PEPPER = "a-test-pepper-long-enough";
    private static final String NID = "1990123456789";

    private final NationalIdProtector protector = new NationalIdProtector(PEPPER);

    @Test
    @DisplayName("the same number always hashes the same, so duplicates are findable")
    void hashingIsDeterministic() {
        assertThat(protector.hash(NID)).isEqualTo(protector.hash(NID));
    }

    @Test
    @DisplayName("the hash does not contain the number it came from")
    void theHashRevealsNothing() {
        String hash = protector.hash(NID);

        assertThat(hash).hasSize(64).doesNotContain(NID);
        assertThat(protector.hash("1990123456780")).isNotEqualTo(hash);
    }

    @Test
    @DisplayName("a different pepper gives a different hash for the same number")
    void thePepperActuallyParticipates() {
        // If it did not, the hash would be a bare digest of a short structured
        // number, and the whole space could be enumerated against a stolen
        // table. This test is what stops the pepper being dropped silently.
        NationalIdProtector other = new NationalIdProtector("a-different-pepper-x");

        assertThat(other.hash(NID)).isNotEqualTo(protector.hash(NID));
    }

    @Test
    @DisplayName("spacing and dashes do not create a second identity")
    void formattingIsNormalisedBeforeHashing() {
        // Typed differently at two branches, the same woman must not enrol
        // twice. Without normalising, the duplicate check silently passes.
        String canonical = protector.hash(NID);

        assertThat(protector.hash("1990 1234 56789")).isEqualTo(canonical);
        assertThat(protector.hash("1990-1234-56789")).isEqualTo(canonical);
        assertThat(protector.hash("  1990123456789  ")).isEqualTo(canonical);
    }

    @Test
    @DisplayName("the mask keeps the last four digits and hides the rest")
    void maskingShowsOnlyWhatStaffNeed() {
        assertThat(protector.mask(NID)).isEqualTo("*********6789");
        assertThat(protector.mask(NID)).hasSameSizeAs(NID);
    }

    @Test
    void aVeryShortNumberIsMaskedEntirely() {
        assertThat(protector.mask("123")).isEqualTo("***");
    }

    @Test
    void rejectsAnEmptyNumber() {
        assertThatThrownBy(() -> protector.hash("   "))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("required");
    }

    @Test
    void rejectsANonNumericNumber() {
        assertThatThrownBy(() -> protector.hash("19901234ABCDE"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("digits");
    }

    @Test
    @DisplayName("a missing or short pepper stops the application, it does not warn")
    void aWeakPepperIsRefusedAtStartup() {
        assertThatThrownBy(() -> new NationalIdProtector(""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.nid.pepper");

        assertThatThrownBy(() -> new NationalIdProtector("too-short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("enumerable");
    }
}
