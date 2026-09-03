package com.homekept.technician;

import com.homekept.AbstractIntegrationTest;
import com.homekept.identity.Role;
import com.homekept.identity.User;
import com.homekept.identity.UserStatus;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link AdminTechnicianController}
 * ({@code GET /api/admin/technicians}, {@code POST /api/admin/technicians}).
 *
 * <p>Runs against a real Postgres via Testcontainers.
 *
 * <p>Covers:
 * <ul>
 *   <li>GET list as ADMIN → 200 array; includes an onboarded technician with identity
 *       fields (firstName, lastName, email, role, userStatus) resolved.</li>
 *   <li>GET list as CUSTOMER → 403; anonymous → 401.</li>
 *   <li>POST as ADMIN → 201 with profile row in the DB.</li>
 *   <li>POST duplicate userId → 409.</li>
 *   <li>POST as CUSTOMER → 403.</li>
 *   <li>POST anonymous → 401.</li>
 * </ul>
 */
class AdminTechnicianIntegrationTest extends AbstractIntegrationTest {

    private static final String TECHNICIANS_URL = "/api/admin/technicians";

    @Autowired TechnicianProfileRepository techProfileRepository;

    private String adminToken;
    private String customerToken;

    private User techUser;

    @BeforeEach
    void seedData() throws Exception {
        long nano = System.nanoTime();

        User adminUser = userRepository.save(new User(
                "admin-tech-admin-" + nano + "@test.local",
                passwordEncoder.encode("Admin1234!"),
                "Admin", "Tech",
                Role.ADMIN, UserStatus.ACTIVE));
        adminToken = loginAs(adminUser.getEmail(), "Admin1234!");

        User customerUser = userRepository.save(new User(
                "admin-tech-cust-" + nano + "@test.local",
                passwordEncoder.encode("Cust1234!"),
                "Customer", "Tech",
                Role.CUSTOMER, UserStatus.ACTIVE));
        customerToken = loginAs(customerUser.getEmail(), "Cust1234!");

        // An existing TECHNICIAN user to be onboarded.
        techUser = userRepository.save(new User(
                "admin-tech-target-" + nano + "@test.local",
                passwordEncoder.encode("Tech1234!"),
                "Target", "Tech",
                Role.TECHNICIAN, UserStatus.ACTIVE));
    }

    // ── GET /api/admin/technicians — list ─────────────────────────────────────

    @Test
    void listTechnicians_asAdmin_returns200WithArray() throws Exception {
        mockMvc.perform(get(TECHNICIANS_URL)
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void listTechnicians_includesOnboardedTechnicianWithIdentityFields() throws Exception {
        Long profileId = onboardTechUser();

        MvcResult result = mockMvc.perform(get(TECHNICIANS_URL)
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        List<Integer> ids = com.jayway.jsonpath.JsonPath.read(body, "$[*].id");
        assertThat(ids).contains(profileId.intValue());

        List<String> firstNames = com.jayway.jsonpath.JsonPath.read(
                body, "$[?(@.id == " + profileId + ")].firstName");
        List<String> emails = com.jayway.jsonpath.JsonPath.read(
                body, "$[?(@.id == " + profileId + ")].email");
        List<String> roles = com.jayway.jsonpath.JsonPath.read(
                body, "$[?(@.id == " + profileId + ")].role");
        List<String> userStatuses = com.jayway.jsonpath.JsonPath.read(
                body, "$[?(@.id == " + profileId + ")].userStatus");

        assertThat(firstNames).containsExactly("Target");
        assertThat(emails).containsExactly(techUser.getEmail());
        assertThat(roles).containsExactly("TECHNICIAN");
        assertThat(userStatuses).containsExactly("ACTIVE");
    }

    @Test
    void listTechnicians_asCustomer_returns403() throws Exception {
        mockMvc.perform(get(TECHNICIANS_URL)
                        .cookie(new Cookie("hk_access", customerToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void listTechnicians_anonymous_returns401() throws Exception {
        mockMvc.perform(get(TECHNICIANS_URL))
                .andExpect(status().isUnauthorized());
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

        Long profileId = idFrom(result);

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
        mockMvc.perform(post(TECHNICIANS_URL)
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

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

    /** Onboards {@code techUser} via the admin POST endpoint and returns the profile id. */
    private Long onboardTechUser() throws Exception {
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
                .andReturn();

        return idFrom(result);
    }
}
