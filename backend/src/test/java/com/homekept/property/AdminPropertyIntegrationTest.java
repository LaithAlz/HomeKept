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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link AdminPropertyController} —
 * {@code PATCH /api/admin/properties/{propertyId}/sku} and
 * {@code GET}/{@code POST /api/admin/properties/{propertyId}/notes}.
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
 *   <li>Notes: add as ADMIN → 201, persists with the admin as author (never a value from
 *       the request body); list newest first; unknown property → 404; blank body → 400;
 *       CUSTOMER/anonymous → 403/401; adding/reading notes never touches the property's
 *       encrypted {@code access_notes} column.</li>
 * </ul>
 */
class AdminPropertyIntegrationTest extends AbstractIntegrationTest {

    private static final String SKU_URL   = "/api/admin/properties/{propertyId}/sku";
    private static final String NOTES_URL = "/api/admin/properties/{propertyId}/notes";

    @Autowired PropertyRepository propertyRepository;
    @Autowired AccessNotesCipher accessNotesCipher;
    @Autowired JdbcTemplate jdbc;

    private String adminToken;
    private String customerToken;
    private User adminUser;
    private Property property;

    @BeforeEach
    void seedData() throws Exception {
        long nano = System.nanoTime();

        adminUser = userRepository.save(new User(
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

    // ── GET/POST /api/admin/properties/{propertyId}/notes ────────────────────

    @Test
    void addNote_asAdmin_returns201AndPersists() throws Exception {
        MvcResult result = mockMvc.perform(post(NOTES_URL, property.getId())
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Dog in the back yard, ring twice.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.body").value("Dog in the back yard, ring twice."))
                .andExpect(jsonPath("$.authorUserId").value(adminUser.getId()))
                .andExpect(jsonPath("$.authorFirstName").value("Admin"))
                .andExpect(jsonPath("$.authorLastName").value("Property"))
                .andReturn();

        Long noteId = idFrom(result);
        Long persistedAuthor = jdbc.queryForObject(
                "SELECT author_user_id FROM property_note WHERE id = ?", Long.class, noteId);
        assertThat(persistedAuthor).isEqualTo(adminUser.getId());
    }

    @Test
    void addNote_authorIsAlwaysThePrincipal_neverTheRequestBody() throws Exception {
        mockMvc.perform(post(NOTES_URL, property.getId())
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Spoofed author attempt\",\"authorUserId\":999999999}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorUserId").value(adminUser.getId()));
    }

    @Test
    void addNote_blankBody_returns400() throws Exception {
        mockMvc.perform(post(NOTES_URL, property.getId())
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void addNote_nonExistentProperty_returns404() throws Exception {
        mockMvc.perform(post(NOTES_URL, 999_999_999L)
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Anything\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void addNote_asCustomer_returns403() throws Exception {
        mockMvc.perform(post(NOTES_URL, property.getId())
                        .cookie(new Cookie("hk_access", customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Anything\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void addNote_asTechnician_returns403() throws Exception {
        // The technician endpoint for this is POST /api/tech/properties/{propertyId}/notes —
        // a TECHNICIAN hitting the ADMIN-gated path must be rejected, same as
        // CUSTOMER/anonymous.
        String techToken = loginAs(Role.TECHNICIAN);

        mockMvc.perform(post(NOTES_URL, property.getId())
                        .cookie(new Cookie("hk_access", techToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Anything\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void addNote_anonymous_returns401() throws Exception {
        mockMvc.perform(post(NOTES_URL, property.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Anything\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listNotes_newestFirst() throws Exception {
        mockMvc.perform(post(NOTES_URL, property.getId())
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"First note\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post(NOTES_URL, property.getId())
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Second note\"}"))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(get(NOTES_URL, property.getId())
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes.length()").value(2))
                .andExpect(jsonPath("$.nextCursor").doesNotExist())
                .andReturn();

        List<String> bodies = com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.notes[*].body");
        // Ordered by createdAt DESC, id DESC (V19's tiebreaker) — deterministic even if both
        // notes land in the same createdAt microsecond.
        assertThat(bodies).containsExactly("Second note", "First note");
    }

    @Test
    void listNotes_nonExistentProperty_returns404() throws Exception {
        mockMvc.perform(get(NOTES_URL, 999_999_999L)
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void listNotes_noHistory_returnsEmptyArray() throws Exception {
        mockMvc.perform(get(NOTES_URL, property.getId())
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").isArray())
                .andExpect(jsonPath("$.notes.length()").value(0))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void listNotes_asCustomer_returns403() throws Exception {
        mockMvc.perform(get(NOTES_URL, property.getId())
                        .cookie(new Cookie("hk_access", customerToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void listNotes_asTechnician_returns403() throws Exception {
        // The technician endpoint for this is GET /api/tech/properties/{propertyId}/notes —
        // a TECHNICIAN hitting the ADMIN-gated path must be rejected. Nothing pinned this
        // before: both suites tested customer-403 and anonymous-401, never the technician,
        // who is the exact principal the ownership rule exists to constrain.
        String techToken = loginAs(Role.TECHNICIAN);

        mockMvc.perform(get(NOTES_URL, property.getId())
                        .cookie(new Cookie("hk_access", techToken)))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/admin/properties/{propertyId}/notes — pagination ────────────

    @Test
    void listNotes_defaultPageSize_returnsTwentyWithNextCursor() throws Exception {
        for (int i = 0; i < 25; i++) {
            addNoteViaApi(property.getId(), "Note " + i);
        }

        MvcResult result = mockMvc.perform(get(NOTES_URL, property.getId())
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes.length()").value(20))
                .andExpect(jsonPath("$.nextCursor").isNumber())
                .andReturn();

        Long nextCursor = idFrom(result, "$.nextCursor");

        mockMvc.perform(get(NOTES_URL + "?cursor=" + nextCursor, property.getId())
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes.length()").value(5))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void listNotes_pagingThroughFillerNotes_stillFindsTheOriginalNote() throws Exception {
        // The regression this pins: a fixed, unpaginated cap made a note permanently
        // unreachable once enough filler notes were posted after it.
        addNoteViaApi(property.getId(), "The note someone wants gone");
        for (int i = 0; i < 100; i++) {
            addNoteViaApi(property.getId(), "Filler " + i);
        }

        List<String> allBodies = new java.util.ArrayList<>();
        Long cursor = null;
        int pages = 0;
        do {
            String url = cursor == null ? NOTES_URL : (NOTES_URL + "?cursor=" + cursor);
            MvcResult page = mockMvc.perform(get(url, property.getId())
                            .cookie(new Cookie("hk_access", adminToken)))
                    .andExpect(status().isOk())
                    .andReturn();
            String body = page.getResponse().getContentAsString();
            allBodies.addAll(com.jayway.jsonpath.JsonPath.read(body, "$.notes[*].body"));
            Object next = com.jayway.jsonpath.JsonPath.read(body, "$.nextCursor");
            cursor = next == null ? null : ((Number) next).longValue();
            pages++;
            assertThat(pages).isLessThan(20);
        } while (cursor != null);

        assertThat(allBodies).hasSize(101);
        assertThat(allBodies).contains("The note someone wants gone");
    }

    @Test
    void notes_neverTouchAccessNotes() throws Exception {
        // Set encrypted access notes directly (as the technician day-sheet flow would),
        // then exercise the plaintext notes log, and confirm the encrypted column is
        // byte-for-byte unchanged and never appears in the notes response.
        byte[] encrypted = accessNotesCipher.encrypt("Lockbox 4471, alarm code 9021");
        property.setAccessNotes(encrypted);
        property = propertyRepository.save(property);

        mockMvc.perform(post(NOTES_URL, property.getId())
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Gate sticks, lift while pushing.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessNotes").doesNotExist())
                .andExpect(jsonPath("$.decryptedAccessNotes").doesNotExist());

        mockMvc.perform(get(NOTES_URL, property.getId())
                        .cookie(new Cookie("hk_access", adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes[0].body").value("Gate sticks, lift while pushing."))
                .andExpect(jsonPath("$.notes[0].accessNotes").doesNotExist());

        Property reloaded = propertyRepository.findById(property.getId()).orElseThrow();
        assertThat(reloaded.getAccessNotes()).isEqualTo(encrypted);
        assertThat(accessNotesCipher.decrypt(reloaded.getAccessNotes()))
                .isEqualTo("Lockbox 4471, alarm code 9021");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Adds a note to the given property via the admin API and returns the created note's id. */
    private Long addNoteViaApi(Long propertyId, String body) throws Exception {
        MvcResult result = mockMvc.perform(post(NOTES_URL, propertyId)
                        .cookie(new Cookie("hk_access", adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"" + body + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return idFrom(result);
    }

}
