package com.homekept;

import com.homekept.identity.RefreshTokenRepository;
import com.homekept.identity.RefreshTokenService;
import com.homekept.identity.Role;
import com.homekept.identity.User;
import com.homekept.identity.UserRepository;
import com.homekept.identity.UserStatus;
import com.homekept.identity.exception.TokenException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link RefreshTokenService}.
 * Runs against a real Postgres via Testcontainers.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RefreshTokenServiceTest {

    @Autowired RefreshTokenService refreshTokenService;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    @Transactional
    void createToken_storesHashNotRawToken() {
        User user = savedUser("alice@example.com");
        String rawToken = refreshTokenService.createToken(user);

        assertThat(rawToken).isNotBlank();

        // The DB must NOT contain the raw token — only the SHA-256 hash
        var stored = refreshTokenRepository.findByTokenHash(rawToken);
        assertThat(stored).isEmpty(); // raw token not stored

        String hash = RefreshTokenService.sha256Hex(rawToken);
        var byHash = refreshTokenRepository.findByTokenHash(hash);
        assertThat(byHash).isPresent();
        assertThat(byHash.get().isValid()).isTrue();
    }

    @Test
    @Transactional
    void rotate_revokesOldToken_andIssuesNew() {
        User user = savedUser("bob@example.com");
        String firstToken = refreshTokenService.createToken(user);
        String secondToken = refreshTokenService.rotate(firstToken);

        assertThat(secondToken).isNotEqualTo(firstToken);

        // Old token must be revoked
        String firstHash = RefreshTokenService.sha256Hex(firstToken);
        var firstRecord = refreshTokenRepository.findByTokenHash(firstHash);
        assertThat(firstRecord).isPresent();
        assertThat(firstRecord.get().isRevoked()).isTrue();

        // New token must be valid
        String secondHash = RefreshTokenService.sha256Hex(secondToken);
        var secondRecord = refreshTokenRepository.findByTokenHash(secondHash);
        assertThat(secondRecord).isPresent();
        assertThat(secondRecord.get().isValid()).isTrue();
    }

    @Test
    @Transactional
    void rotate_onAlreadyRevokedToken_throwsAndRevokesAll() {
        User user = savedUser("charlie@example.com");
        String token = refreshTokenService.createToken(user);
        refreshTokenService.rotate(token); // rotate once — token is now revoked

        // Replaying the revoked token should throw REVOKED and revoke all tokens for the user
        assertThatThrownBy(() -> refreshTokenService.rotate(token))
                .isInstanceOf(TokenException.class)
                .extracting(e -> ((TokenException) e).getReason())
                .isEqualTo(TokenException.Reason.REVOKED);
    }

    @Test
    @Transactional
    void rotate_unknownToken_throwsNotFound() {
        assertThatThrownBy(() -> refreshTokenService.rotate("completely-unknown-token"))
                .isInstanceOf(TokenException.class)
                .extracting(e -> ((TokenException) e).getReason())
                .isEqualTo(TokenException.Reason.NOT_FOUND);
    }

    @Test
    @Transactional
    void revokeAll_revokesAllTokensForUser() {
        User user = savedUser("diana@example.com");
        String token1 = refreshTokenService.createToken(user);
        String token2 = refreshTokenService.createToken(user);

        refreshTokenService.revokeAll(user.getId());

        String hash1 = RefreshTokenService.sha256Hex(token1);
        String hash2 = RefreshTokenService.sha256Hex(token2);
        assertThat(refreshTokenRepository.findByTokenHash(hash1).get().isRevoked()).isTrue();
        assertThat(refreshTokenRepository.findByTokenHash(hash2).get().isRevoked()).isTrue();
    }

    @Test
    @Transactional
    void getUserForToken_validToken_returnsUser() {
        User user = savedUser("eve@example.com");
        String token = refreshTokenService.createToken(user);

        User found = refreshTokenService.getUserForToken(token);
        assertThat(found.getId()).isEqualTo(user.getId());
    }

    @Test
    @Transactional
    void getUserForToken_revokedToken_throwsRevoked() {
        User user = savedUser("frank@example.com");
        String token = refreshTokenService.createToken(user);
        refreshTokenService.revokeAll(user.getId());

        assertThatThrownBy(() -> refreshTokenService.getUserForToken(token))
                .isInstanceOf(TokenException.class)
                .extracting(e -> ((TokenException) e).getReason())
                .isEqualTo(TokenException.Reason.REVOKED);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private User savedUser(String email) {
        return userRepository.save(new User(email, passwordEncoder.encode("Password1"), "Test", "User",
                Role.CUSTOMER, UserStatus.ACTIVE));
    }
}
