package com.homekept.property;

import com.homekept.config.AppProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM column-level encryption for {@code property.access_notes}.
 *
 * <h2>Storage format</h2>
 * <pre>
 *   BYTEA = IV (12 bytes) || ciphertext (variable) || GCM auth tag (16 bytes)
 * </pre>
 *
 * <h2>Key binding</h2>
 * <p>Key comes from {@code app.encryption.access-notes-key} (environment variable
 * {@code ACCESS_NOTES_ENC_KEY}): a Base64-encoded 32-byte (256-bit) value.
 *
 * <h2>Startup guard</h2>
 * <p>When not in dev-mode the guard rejects a blank or wrong-length key, so the
 * application fails fast rather than running with no encryption or a truncated key.
 * In dev-mode, access notes encryption is silently disabled (encrypt/decrypt are
 * no-ops that return {@code null}) — there are no real properties in dev.
 *
 * <h2>Security properties</h2>
 * <ul>
 *   <li>Random 12-byte IV per encryption — two encryptions of the same plaintext
 *       produce different ciphertexts (IND-CPA).</li>
 *   <li>GCM auth tag (128 bits) detects tampering (IND-CCA2). Decryption throws
 *       {@link javax.crypto.AEADBadTagException} if the ciphertext or IV was modified.</li>
 *   <li>The raw key is never logged.</li>
 *   <li>The plaintext is never logged.</li>
 * </ul>
 *
 * <p><strong>Access notes are decrypted only by the technician day-sheet (a later slice).
 * No other code path should call {@link #decrypt}.</strong>
 */
@Component
public class AccessNotesCipher {

    private static final Logger log = LoggerFactory.getLogger(AccessNotesCipher.class);

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int KEY_LENGTH_BYTES = 32; // 256 bits

    private final SecureRandom secureRandom = new SecureRandom();
    private final boolean devMode;
    private final String rawKey;

    private SecretKey secretKey; // null when devMode=true and key is blank

    public AccessNotesCipher(AppProperties appProperties) {
        this.devMode = appProperties.devMode();
        this.rawKey = appProperties.encryption().accessNotesKey();
    }

    /**
     * Startup guard: validates key length and initialises the {@link SecretKey}.
     * In dev-mode, a blank key is silently tolerated (encryption disabled).
     */
    @PostConstruct
    void initialise() {
        if (rawKey == null || rawKey.isBlank()) {
            if (devMode) {
                log.warn("ACCESS_NOTES_ENC_KEY is blank — access-note encryption disabled in dev-mode.");
                this.secretKey = null;
                return;
            }
            throw new IllegalStateException(
                    "ACCESS_NOTES_ENC_KEY must not be blank in production. "
                    + "Set a Base64-encoded 32-byte random value: "
                    + "openssl rand -base64 32");
        }

        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(rawKey);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "ACCESS_NOTES_ENC_KEY is not valid Base64. "
                    + "Generate one with: openssl rand -base64 32", e);
        }

        if (keyBytes.length != KEY_LENGTH_BYTES) {
            throw new IllegalStateException(
                    "ACCESS_NOTES_ENC_KEY must decode to exactly 32 bytes (256 bits). "
                    + "Got " + keyBytes.length + " bytes. "
                    + "Generate one with: openssl rand -base64 32");
        }

        this.secretKey = new SecretKeySpec(keyBytes, "AES");
        log.info("AccessNotesCipher: AES-256-GCM key loaded ({} bytes).", keyBytes.length);
    }

    /**
     * Encrypts plaintext access notes with a fresh random IV.
     *
     * @param plaintext the raw access notes string; must not be null
     * @return {@code IV (12 bytes) || ciphertext || GCM tag (16 bytes)} as a byte array,
     *         or {@code null} if encryption is disabled in dev-mode
     * @throws IllegalStateException if the cipher operation fails
     */
    public byte[] encrypt(String plaintext) {
        if (secretKey == null) {
            // Dev-mode with no key — store nothing
            return null;
        }
        if (plaintext == null) {
            return null;
        }

        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] ciphertextAndTag = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            byte[] result = new byte[IV_LENGTH_BYTES + ciphertextAndTag.length];
            System.arraycopy(iv, 0, result, 0, IV_LENGTH_BYTES);
            System.arraycopy(ciphertextAndTag, 0, result, IV_LENGTH_BYTES, ciphertextAndTag.length);
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Access-note encryption failed", e);
        }
    }

    /**
     * Decrypts encrypted access notes.
     *
     * <p><strong>NEVER log the return value. Only call from the technician day-sheet slice.</strong>
     *
     * @param encrypted the stored {@code IV || ciphertext || tag} blob
     * @return the plaintext string, or {@code null} if {@code encrypted} is null/empty or
     *         encryption is disabled in dev-mode
     * @throws IllegalStateException    if the cipher operation fails for a non-auth-tag reason
     * @throws javax.crypto.AEADBadTagException (wrapped in IllegalStateException) if the data
     *         was tampered with
     */
    public String decrypt(byte[] encrypted) {
        if (secretKey == null || encrypted == null || encrypted.length == 0) {
            return null;
        }

        // Length guard runs OUTSIDE the try so its specific message is not swallowed
        // and rewrapped by the generic catch below.
        if (encrypted.length < IV_LENGTH_BYTES + GCM_TAG_LENGTH_BITS / 8) {
            throw new IllegalStateException(
                    "Encrypted access notes blob is too short to contain IV + tag");
        }

        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            System.arraycopy(encrypted, 0, iv, 0, IV_LENGTH_BYTES);

            byte[] ciphertextAndTag = new byte[encrypted.length - IV_LENGTH_BYTES];
            System.arraycopy(encrypted, IV_LENGTH_BYTES, ciphertextAndTag, 0, ciphertextAndTag.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] plaintext = cipher.doFinal(ciphertextAndTag);
            return new String(plaintext, java.nio.charset.StandardCharsets.UTF_8);
        } catch (javax.crypto.AEADBadTagException e) {
            throw new IllegalStateException("Access-note decryption failed: authentication tag mismatch — data may have been tampered with", e);
        } catch (Exception e) {
            throw new IllegalStateException("Access-note decryption failed", e);
        }
    }
}
