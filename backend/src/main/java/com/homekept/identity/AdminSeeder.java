package com.homekept.identity;

import com.homekept.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the first ADMIN user from environment variables on startup.
 *
 * <p>Reads {@code app.admin-seed.email} (env {@code ADMIN_SEED_EMAIL}) and
 * {@code app.admin-seed.password} (env {@code ADMIN_SEED_PASSWORD}). When both
 * are non-blank and no user with that email already exists (case-insensitive),
 * an ADMIN/ACTIVE user is created with the supplied credentials.
 *
 * <p>Idempotent: if the user already exists it is never modified. Safe to run on
 * every boot.
 *
 * <p>The password and its hash are never logged. Only the email address is emitted
 * at INFO level on a successful seed.
 */
@Component
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final AppProperties appProperties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminSeeder(AppProperties appProperties,
                       UserRepository userRepository,
                       PasswordEncoder passwordEncoder) {
        this.appProperties = appProperties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppProperties.AdminSeed seed = appProperties.adminSeed();

        String rawEmail    = seed.email();
        String rawPassword = seed.password();

        if (rawEmail == null || rawEmail.isBlank()
                || rawPassword == null || rawPassword.isBlank()) {
            log.debug("AdminSeeder: ADMIN_SEED_EMAIL / ADMIN_SEED_PASSWORD not set — skipping");
            return;
        }

        // Normalize email: strip whitespace, lowercase — same convention used in AuthService.
        String email = rawEmail.strip().toLowerCase(java.util.Locale.ROOT);

        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            log.debug("AdminSeeder: admin user already exists, skipping");
            return;
        }

        String hash = passwordEncoder.encode(rawPassword);
        User admin = new User(email, hash, "Admin", "User", Role.ADMIN, UserStatus.ACTIVE);
        userRepository.save(admin);

        log.info("AdminSeeder: seeded admin user {}", email);
    }
}
