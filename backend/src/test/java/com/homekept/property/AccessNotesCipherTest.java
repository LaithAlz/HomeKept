package com.homekept.property;

import com.homekept.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure unit tests for {@link AccessNotesCipher} — no Spring context.
 *
 * <p>{@link AppProperties} is constructed directly (it is a record with a canonical
 * constructor) so no Mockito is needed. The test key is 32 bytes of 0x41 ('A')
 * base64-encoded, matching the placeholder in {@code src/test/resources/application.yml}.
 *
 * <p>Covers:
 * <ul>
 *   <li>Encrypt → decrypt round-trip returns original plaintext.</li>
 *   <li>Encrypting the same plaintext twice produces different ciphertexts (random IV).</li>
 *   <li>Tampering with a ciphertext byte causes decrypt to throw (GCM auth tag).</li>
 *   <li>Null plaintext passed to encrypt returns null.</li>
 *   <li>Null / empty bytes passed to decrypt return null.</li>
 *   <li>A blob too short for IV + tag throws IllegalStateException.</li>
 *   <li>Dev-mode with blank key: encrypt/decrypt are no-ops returning null.</li>
 *   <li>Prod-mode startup guard rejects blank key, invalid Base64, or wrong length.</li>
 * </ul>
 */
class AccessNotesCipherTest {

    // 32 bytes of 0x41 ('A') — QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUA=
    private static final String TEST_KEY = "QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUA=";

    private AccessNotesCipher cipher;

    @BeforeEach
    void setUp() {
        cipher = buildCipher(false, TEST_KEY);
    }

    // ── Round-trip ────────────────────────────────────────────────────────────

    @Test
    void encryptThenDecrypt_returnsOriginalPlaintext() {
        String plaintext = "Gate code: #1234. Key under the red pot on the left.";
        byte[] ciphertext = cipher.encrypt(plaintext);
        assertThat(cipher.decrypt(ciphertext)).isEqualTo(plaintext);
    }

    @Test
    void encryptThenDecrypt_shortString_roundTrips() {
        byte[] ciphertext = cipher.encrypt("A");
        assertThat(cipher.decrypt(ciphertext)).isEqualTo("A");
    }

    @Test
    void encryptThenDecrypt_longString_roundTrips() {
        String plaintext = "A".repeat(2000);
        byte[] ciphertext = cipher.encrypt(plaintext);
        assertThat(cipher.decrypt(ciphertext)).isEqualTo(plaintext);
    }

    @Test
    void encryptThenDecrypt_unicodeString_roundTrips() {
        String plaintext = "Clé: #4242. Maison de gauche — Büro.";
        byte[] ciphertext = cipher.encrypt(plaintext);
        assertThat(cipher.decrypt(ciphertext)).isEqualTo(plaintext);
    }

    // ── Probabilistic uniqueness (random IV) ─────────────────────────────────

    @Test
    void encrypt_samePlaintextTwice_producesDifferentCiphertexts() {
        byte[] first  = cipher.encrypt("same message");
        byte[] second = cipher.encrypt("same message");
        // The raw bytes must differ — two different IVs were used.
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void encrypt_manySamePlaintexts_allCiphertextsDiffer() {
        List<byte[]> results = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            results.add(cipher.encrypt("same"));
        }
        // Verify no two blobs are byte-for-byte identical (birthday negligible with 12-byte IV).
        for (int i = 0; i < results.size(); i++) {
            for (int j = i + 1; j < results.size(); j++) {
                assertThat(results.get(i))
                        .as("Ciphertexts %d and %d must differ", i, j)
                        .isNotEqualTo(results.get(j));
            }
        }
    }

    // ── GCM authentication tag — tampering detection ──────────────────────────

    @Test
    void decrypt_tamperedCiphertextByte_throws() {
        byte[] ciphertext = cipher.encrypt("top secret");
        // Flip a byte in the ciphertext/tag area (beyond the 12-byte IV).
        ciphertext[13] ^= 0xFF;

        assertThatThrownBy(() -> cipher.decrypt(ciphertext))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("decryption failed");
    }

    @Test
    void decrypt_tamperedIvByte_throws() {
        byte[] ciphertext = cipher.encrypt("top secret");
        // Flip a byte inside the 12-byte IV (index 0..11).
        ciphertext[0] ^= 0xFF;

        assertThatThrownBy(() -> cipher.decrypt(ciphertext))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("decryption failed");
    }

    @Test
    void decrypt_blobTruncatedToIvLength_throws() {
        byte[] ciphertext = cipher.encrypt("hello");
        // Retain only the 12-byte IV — not enough for ciphertext + tag.
        byte[] truncated = Arrays.copyOf(ciphertext, 12);

        assertThatThrownBy(() -> cipher.decrypt(truncated))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void decrypt_blobTooShortForIvPlusTag_throws() {
        // Minimum valid blob = IV (12) + GCM tag (16) = 28 bytes.
        // A 27-byte blob must be rejected by the length check before the cipher runs.
        byte[] tooShort = new byte[27];

        assertThatThrownBy(() -> cipher.decrypt(tooShort))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("too short");
    }

    // ── Null / empty contract ─────────────────────────────────────────────────

    @Test
    void encrypt_nullPlaintext_returnsNull() {
        assertThat(cipher.encrypt(null)).isNull();
    }

    @Test
    void decrypt_nullBytes_returnsNull() {
        assertThat(cipher.decrypt(null)).isNull();
    }

    @Test
    void decrypt_emptyByteArray_returnsNull() {
        assertThat(cipher.decrypt(new byte[0])).isNull();
    }

    // ── Dev-mode (encryption disabled) ───────────────────────────────────────

    @Test
    void devMode_blankKey_encryptReturnsNull() {
        AccessNotesCipher devCipher = buildCipher(true, "");
        assertThat(devCipher.encrypt("anything")).isNull();
    }

    @Test
    void devMode_blankKey_decryptReturnsNull() {
        AccessNotesCipher devCipher = buildCipher(true, "");
        assertThat(devCipher.decrypt(new byte[]{1, 2, 3})).isNull();
    }

    // ── Startup guard (prod mode) ─────────────────────────────────────────────

    @Test
    void prodMode_blankKey_throwsOnInitialise() {
        AccessNotesCipher prodCipher = buildUninitialised(false, "");

        assertThatThrownBy(prodCipher::initialise)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ACCESS_NOTES_ENC_KEY must not be blank");
    }

    @Test
    void prodMode_nonBase64Key_throwsOnInitialise() {
        AccessNotesCipher prodCipher = buildUninitialised(false, "not-valid-base64!!!");

        assertThatThrownBy(prodCipher::initialise)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not valid Base64");
    }

    @Test
    void prodMode_tooShortKey_throwsOnInitialise() {
        // Base64 of 16 bytes (too short — need 32).
        String shortKey = java.util.Base64.getEncoder().encodeToString(new byte[16]);
        AccessNotesCipher prodCipher = buildUninitialised(false, shortKey);

        assertThatThrownBy(prodCipher::initialise)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Builds an {@link AccessNotesCipher} and calls {@code initialise()} on it. */
    private static AccessNotesCipher buildCipher(boolean devMode, String keyBase64) {
        AccessNotesCipher c = buildUninitialised(devMode, keyBase64);
        c.initialise();
        return c;
    }

    /** Builds an {@link AccessNotesCipher} WITHOUT calling {@code initialise()}. */
    private static AccessNotesCipher buildUninitialised(boolean devMode, String keyBase64) {
        AppProperties props = new AppProperties(
                "America/Toronto",
                false,   // secureCookies
                devMode,
                new AppProperties.Cors(List.of("http://localhost:5173")),
                new AppProperties.Jwt("test-signing-key-placeholder-xx", 900L, 604800L),
                new AppProperties.Encryption(keyBase64),
                new AppProperties.AdminSeed("", "")
        );
        return new AccessNotesCipher(props);
    }
}
