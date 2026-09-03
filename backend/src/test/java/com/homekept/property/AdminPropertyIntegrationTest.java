package com.homekept.property;

import com.homekept.AbstractIntegrationTest;
import com.homekept.identity.Role;
import com.homekept.identity.User;
import com.homekept.identity.UserStatus;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link AdminPropertyController} —
 * {@code PATCH /api/admin/properties/{propertyId}/sku}.
 *
 * <p>Covers:
 * <ul>
 *   <li>PATCH as ADMIN → 200; property row updated (verified via repository reload).</li>
 *   <li>PATCH as ADMIN with a partial follow-up request → previously-set fields untouched
 *       (partial-update semantics, matches other admin PATCH endpoints).</li>
 *   <li>PATCH as CUSTOMER → 403.</li>
 *   <li>PATCH anonymous → 401.</li>
 *   <li>PATCH unknown propertyId → 404.</li>
 *   <li>PATCH waterHeaterAgeYears = -1 → 400 validation error.</li>
 *   <li>PATCH waterHeaterAgeYears = 101 → 400 validation error.</li>
 * </ul>
 */
class AdminPropertyIntegrationTest extends AbstractIntegrationTest {

    private static final String SKU_URL   = "/api/admin/properties/{propertyId}/sku";

    @Autowired PropertyRepository propertyRepository;

    private String adminToken;
    private String customerToken;
    private Property property;

    @BeforeEach
    void seedData() throws Exception {
        long nano = System.nanoTime();

        User adminUser = userRepository.save(new User(
                "admin-property-admin-" + nano + "@test.local",
                passwordEncoder.encode("Test1234!"),
                "Admin", "Property",
                Role.ADMIN, UserStatus.ACTIVE));
        adminToken = loginAs(adminUser.getEmail(), "Test1234!");

        User customerUser = userRepository.save(new User(
                "admin-property-customer-" + nano + "@test.local",
                passwordEncoder.encode("Test1234!"),
                "Customer", "Property",
                Role.CUSTOMER, UserStatus.ACTIVE));
        customerToken = loginAs(customerUser.getEmail(), "Test1234!");

        property = propertyRepository.save(new Property(
                nano + " Sku Sheet Ave", null, "Mississauga", "L5L 1A1",
                "L5L", null, null, PropertyType.DETACHED));
    }

    // ── PATCH /api/admin/properties/{propertyId}/sku ─────────────────────────

    @Test
    void updateSku_asAdmin_returns200AndPersists() throws Exception {
        String body = """
                {
                  "hvacFilterSizes": "16x25x1 (x2), 20x20x1 (x1)",
                  "smokeCoDetectorModels": "Kidde P4010ACSCO-CA",
                  "humidifierModel": "Aprilaire 600",
                  "waterHeaterAgeYears": 6,
                  "waterHeaterFlushEligible": true
                }
                """;

        mockMvc.perform(patch(SKU_URL, property.getId())
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.propertyId").value(property.getId()))
                .andExpect(jsonPath("$.hvacFilterSizes").value("16x25x1 (x2), 20x20x1 (x1)"))
                .andExpect(jsonPath("$.smokeCoDetectorModels").value("Kidde P4010ACSCO-CA"))
                .andExpect(jsonPath("$.humidifierModel").value("Aprilaire 600"))
                .andExpect(jsonPath("$.waterHeaterAgeYears").value(6))
                .andExpect(jsonPath("$.waterHeaterFlushEligible").value(true));

        Property reloaded = propertyRepository.findById(property.getId()).orElseThrow();
        assertThat(reloaded.getHvacFilterSizes()).isEqualTo("16x25x1 (x2), 20x20x1 (x1)");
        assertThat(reloaded.getSmokeCODetectorModels()).isEqualTo("Kidde P4010ACSCO-CA");
        assertThat(reloaded.getHumidifierModel()).isEqualTo("Aprilaire 600");
        assertThat(reloaded.getWaterHeaterAgeYears()).isEqualTo(6);
        assertThat(reloaded.getWaterHeaterFlushEligible()).isTrue();
    }

    @Test
    void updateSku_partialFollowUp_leavesPreviouslySetFieldsUntouched() throws Exception {
        // First PATCH sets all five fields.
        mockMvc.perform(patch(SKU_URL, property.getId())
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "hvacFilterSizes": "16x25x1",
                                  "smokeCoDetectorModels": "Kidde P4010",
                                  "humidifierModel": "Aprilaire 600",
                                  "waterHeaterAgeYears": 4,
                                  "waterHeaterFlushEligible": true
                                }
                                """))
                .andExpect(status().isOk());

        // Second PATCH only updates humidifierModel.
        mockMvc.perform(patch(SKU_URL, property.getId())
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "humidifierModel": "Honeywell HE365"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.humidifierModel").value("Honeywell HE365"))
                // Fields not included in this PATCH remain from the first request.
                .andExpect(jsonPath("$.hvacFilterSizes").value("16x25x1"))
                .andExpect(jsonPath("$.smokeCoDetectorModels").value("Kidde P4010"))
                .andExpect(jsonPath("$.waterHeaterAgeYears").value(4))
                .andExpect(jsonPath("$.waterHeaterFlushEligible").value(true));
    }

    @Test
    void updateSku_asCustomer_returns403() throws Exception {
        mockMvc.perform(patch(SKU_URL, property.getId())
                        .cookie(new Cookie("hk_access", customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"humidifierModel\":\"Aprilaire 600\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateSku_anonymous_returns401() throws Exception {
        mockMvc.perform(patch(SKU_URL, property.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"humidifierModel\":\"Aprilaire 600\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateSku_unknownPropertyId_returns404() throws Exception {
        mockMvc.perform(patch(SKU_URL, 999_999_999L)
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"humidifierModel\":\"Aprilaire 600\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateSku_waterHeaterAgeYearsNegative_returns400() throws Exception {
        mockMvc.perform(patch(SKU_URL, property.getId())
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"waterHeaterAgeYears\": -1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void updateSku_waterHeaterAgeYearsTooLarge_returns400() throws Exception {
        mockMvc.perform(patch(SKU_URL, property.getId())
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"waterHeaterAgeYears\": 101}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

}
