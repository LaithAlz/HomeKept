package com.homekept.technician;

import com.homekept.TestcontainersConfiguration;
import com.homekept.identity.Role;
import com.homekept.identity.User;
import com.homekept.identity.UserRepository;
import com.homekept.identity.UserStatus;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link AdminTechnicianController}
 * ({@code POST /api/admin/technicians}).
 *
 * <p>Runs against a real Postgres via Testcontainers.
 *
 * <p>Covers:
 * <ul>
 *   <li>POST as ADMIN → 201 with profile row in the DB.</li>
 *   <li>POST duplicate userId → 409.</li>
 *   <li>POST as CUSTOMER → 403.</li>
 *   <li>POST anonymous → 401.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AdminTechnicianIntegrationTest {

    private static final String LOGIN_URL       = "/api/auth/login";
    private static final String TECHNICIANS_URL = "/api/admin/technicians";

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired TechnicianProfileRepository techProfileRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private final List<Long> createdTechProfileIds = new ArrayList<>();
    private final List<Long> createdUserIds        = new ArrayList<>();

    private User adminUser;
    private String adminToken;

    private User customerUser;
    private String customerToken;

    private User techUser;

    @BeforeEach
    void seedData() throws Exception {
        long nano = System.nanoTime();

        adminUser = userRepository.save(new User(
                "admin-tech-admin-" + nano + "@test.local",
                passwordEncoder.encode("Admin1234!"),
                "Admin", "Tech",
                Role.ADMIN, UserStatus.ACTIVE));
        createdUserIds.add(adminUser.getId());
        adminToken = loginAsUser(adminUser.getEmail(), "Admin1234!");

        customerUser = userRepository.save(new User(
                "admin-tech-cust-" + nano + "@test.local",
                passwordEncoder.encode("Cust1234!"),
                "Customer", "Tech",
                Role.CUSTOMER, UserStatus.ACTIVE));
        createdUserIds.add(customerUser.getId());
        customerToken = loginAsUser(customerUser.getEmail(), "Cust1234!");

        // An existing TECHNICIAN user to be onboarded.
        techUser = userRepository.save(new User(
                "admin-tech-target-" + nano + "@test.local",
                passwordEncoder.encode("Tech1234!"),
                "Target", "Tech",
                Role.TECHNICIAN, UserStatus.ACTIVE));
        createdUserIds.add(techUser.getId());
    }

    @AfterEach
    void tearDown() {
        for (Long profId : createdTechProfileIds) {
            techProfileRepository.deleteById(profId);
        }
        createdTechProfileIds.clear();

        for (Long userId : createdUserIds) {
            userRepository.deleteById(userId);
        }
        createdUserIds.clear();
    }

    // ── POST /api/admin/technicians — happy path ──────────────────────────────

    @Test
    void createTechnician_asAdmin_returns201AndPersistsProfile() throws Exception {
        String body = """
                {
                  "userId": %d,
                  "fullyLoadedHourlyCostCents": 4300,
                  "employeeStatus": "ACTIVE"
                }
                """.formatted(techUser.getId());

        MvcResult result = mockMvc.perform(post(TECHNICIANS_URL)
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.userId").value(techUser.getId()))
                .andExpect(jsonPath("$.fullyLoadedHourlyCostCents").value(4300))
                .andReturn();

        Long profileId = ((Number) com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.id")).longValue();
        createdTechProfileIds.add(profileId);

        // Assert the row actually exists in the DB.
        TechnicianProfile persisted = techProfileRepository.findById(profileId).orElseThrow();
        assertThat(persisted.getUserId()).isEqualTo(techUser.getId());
        assertThat(persisted.getFullyLoadedHourlyCostCents()).isEqualTo(4300);
    }

    // ── POST /api/admin/technicians — duplicate userId → 409 ─────────────────

    @Test
    void createTechnician_duplicateUserId_returns409() throws Exception {
        String body = """
                {
                  "userId": %d,
                  "fullyLoadedHourlyCostCents": 4300
                }
                """.formatted(techUser.getId());

        // First creation — success.
        MvcResult first = mockMvc.perform(post(TECHNICIANS_URL)
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        Long profileId = ((Number) com.jayway.jsonpath.JsonPath.read(
                first.getResponse().getContentAsString(), "$.id")).longValue();
        createdTechProfileIds.add(profileId);

        // Second creation for the same userId — must return 409.
        mockMvc.perform(post(TECHNICIANS_URL)
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    // ── POST /api/admin/technicians — role gating ─────────────────────────────

    @Test
    void createTechnician_asCustomer_returns403() throws Exception {
        String body = """
                {
                  "userId": %d,
                  "fullyLoadedHourlyCostCents": 4300
                }
                """.formatted(techUser.getId());

        mockMvc.perform(post(TECHNICIANS_URL)
                        .cookie(new Cookie("hk_access", customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void createTechnician_anonymous_returns401() throws Exception {
        String body = """
                {
                  "userId": %d,
                  "fullyLoadedHourlyCostCents": 4300
                }
                """.formatted(techUser.getId());

        mockMvc.perform(post(TECHNICIANS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String loginAsUser(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return extractCookieValue(result.getResponse().getHeaders("Set-Cookie"), "hk_access");
    }

    private String extractCookieValue(List<String> setCookieHeaders, String name) {
        return setCookieHeaders.stream()
                .filter(h -> h.startsWith(name + "="))
                .map(h -> h.split(";")[0].substring(name.length() + 1))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Cookie not found: " + name));
    }
}
