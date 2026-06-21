package com.homekept;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the catalog vertical slice.
 *
 * <p>Runs against a real Postgres via Testcontainers. Flyway runs V1 + V2 migrations
 * on startup; JPA validates against the resulting schema (ddl-auto: validate).
 *
 * <p>Covers:
 * <ul>
 *   <li>GET /api/catalog/plans reachable without auth (public)</li>
 *   <li>Returns 3 tiers in correct order (ESSENTIAL, COMPLETE, PREMIER)</li>
 *   <li>Exact prices, inclusions, and founding rate values per docs/pricing-and-visits.md</li>
 *   <li>Services array populated from plan_tier_service seed</li>
 *   <li>GET /api/catalog/picks reachable without auth</li>
 *   <li>Picks grouped by BASIC/MEDIUM/PREMIUM with correct à la carte prices</li>
 *   <li>Pick counts match the seed (5 BASIC, 5 MEDIUM, 4 PREMIUM)</li>
 *   <li>Standing items are excluded from picks (is_free_with_every_visit = true)</li>
 *   <li>A protected endpoint (GET /api/auth/me) still requires auth — allowlist not over-opened</li>
 *   <li>Flyway V2 + JPA validate boots cleanly (implicit — if the test context starts, it passed)</li>
 *   <li>COMPLETE returns foundingRateAvailable=true with the default FoundingRateAvailability (slots open)</li>
 *   <li>When FoundingRateAvailability returns false, COMPLETE reports foundingRateAvailable=false
 *       while foundingMonthlyPriceCents remains 12900 (price not hidden, just availability toggled)</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CatalogIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    private static final String PLANS_URL = "/api/catalog/plans";
    private static final String PICKS_URL = "/api/catalog/picks";
    private static final String ME_URL    = "/api/auth/me";

    // ── /api/catalog/plans — public access ───────────────────────────────────

    @Test
    void plans_isPublic_noAuthRequired() throws Exception {
        mockMvc.perform(get(PLANS_URL))
                .andExpect(status().isOk());
    }

    @Test
    void plans_returns3Tiers() throws Exception {
        mockMvc.perform(get(PLANS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void plans_orderedByPrice_essentialFirst() throws Exception {
        mockMvc.perform(get(PLANS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("ESSENTIAL"))
                .andExpect(jsonPath("$[1].code").value("COMPLETE"))
                .andExpect(jsonPath("$[2].code").value("PREMIER"));
    }

    // ── ESSENTIAL — exact values from docs/pricing-and-visits.md ─────────────

    @Test
    void plans_essential_exactPrices() throws Exception {
        mockMvc.perform(get(PLANS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].displayName").value("Essential"))
                .andExpect(jsonPath("$[0].monthlyPriceCents").value(8900))
                .andExpect(jsonPath("$[0].annualPriceCents").value(89000))
                .andExpect(jsonPath("$[0].visitsPerYear").value(4));
    }

    @Test
    void plans_essential_picksAndFounding() throws Exception {
        mockMvc.perform(get(PLANS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].includedPicksPerYear").value(1))
                .andExpect(jsonPath("$[0].maxPremiumPicksPerYear").value(0))
                .andExpect(jsonPath("$[0].foundingRateAvailable").value(false))
                .andExpect(jsonPath("$[0].foundingMonthlyPriceCents").value(nullValue()));
    }

    // ── COMPLETE — exact values from docs/pricing-and-visits.md ──────────────

    @Test
    void plans_complete_exactPrices() throws Exception {
        mockMvc.perform(get(PLANS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].displayName").value("Complete"))
                .andExpect(jsonPath("$[1].monthlyPriceCents").value(14900))
                .andExpect(jsonPath("$[1].annualPriceCents").value(149000))
                .andExpect(jsonPath("$[1].visitsPerYear").value(8));
    }

    @Test
    void plans_complete_picksAndFounding() throws Exception {
        mockMvc.perform(get(PLANS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].includedPicksPerYear").value(3))
                .andExpect(jsonPath("$[1].maxPremiumPicksPerYear").value(1))
                .andExpect(jsonPath("$[1].foundingRateAvailable").value(true))
                .andExpect(jsonPath("$[1].foundingMonthlyPriceCents").value(12900));
    }

    // ── PREMIER — exact values from docs/pricing-and-visits.md ───────────────

    @Test
    void plans_premier_exactPrices() throws Exception {
        mockMvc.perform(get(PLANS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[2].displayName").value("Premier"))
                .andExpect(jsonPath("$[2].monthlyPriceCents").value(24900))
                .andExpect(jsonPath("$[2].annualPriceCents").value(249000))
                .andExpect(jsonPath("$[2].visitsPerYear").value(12));
    }

    @Test
    void plans_premier_picksAndFounding() throws Exception {
        mockMvc.perform(get(PLANS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[2].includedPicksPerYear").value(6))
                .andExpect(jsonPath("$[2].maxPremiumPicksPerYear").value(3))
                .andExpect(jsonPath("$[2].foundingRateAvailable").value(false))
                .andExpect(jsonPath("$[2].foundingMonthlyPriceCents").value(nullValue()));
    }

    // ── Services array populated ──────────────────────────────────────────────

    @Test
    void plans_essential_servicesArrayHas4StandingItems() throws Exception {
        // ESSENTIAL has 4 standing-item rows in plan_tier_service
        mockMvc.perform(get(PLANS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].services.length()").value(4));
    }

    @Test
    void plans_essential_standingServicesHaveFrequency4() throws Exception {
        // Every standing-item service for ESSENTIAL runs 4 times/year (once per visit)
        mockMvc.perform(get(PLANS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].services[0].frequencyPerYear").value(4))
                .andExpect(jsonPath("$[0].services[1].frequencyPerYear").value(4))
                .andExpect(jsonPath("$[0].services[2].frequencyPerYear").value(4))
                .andExpect(jsonPath("$[0].services[3].frequencyPerYear").value(4));
    }

    @Test
    void plans_complete_servicesHaveFrequency8() throws Exception {
        mockMvc.perform(get(PLANS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].services[0].frequencyPerYear").value(8));
    }

    @Test
    void plans_premier_servicesHaveFrequency12() throws Exception {
        mockMvc.perform(get(PLANS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[2].services[0].frequencyPerYear").value(12));
    }

    @Test
    void plans_services_hasTierClassField() throws Exception {
        // All standing items are BASIC tier_class
        mockMvc.perform(get(PLANS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].services[0].tierClass").value("BASIC"));
    }

    // ── /api/catalog/picks — public access ───────────────────────────────────

    @Test
    void picks_isPublic_noAuthRequired() throws Exception {
        mockMvc.perform(get(PICKS_URL))
                .andExpect(status().isOk());
    }

    @Test
    void picks_hasThreeGroups() throws Exception {
        mockMvc.perform(get(PICKS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.basic").exists())
                .andExpect(jsonPath("$.medium").exists())
                .andExpect(jsonPath("$.premium").exists());
    }

    @Test
    void picks_basicGroup_correctPriceAndCount() throws Exception {
        // 5 BASIC picks: extra filter visit, weatherstripping touch-up, garage door tune,
        // faucet/showerhead descale, detector battery sweep
        mockMvc.perform(get(PICKS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.basic.aLaCartePriceCents").value(4900))
                .andExpect(jsonPath("$.basic.services.length()").value(5));
    }

    @Test
    void picks_mediumGroup_correctPriceAndCount() throws Exception {
        // 5 MEDIUM picks: extra water heater flush, dryer vent deep clean,
        // caulking refresh, smart thermostat install, toilet internals refresh
        mockMvc.perform(get(PICKS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.medium.aLaCartePriceCents").value(8900))
                .andExpect(jsonPath("$.medium.services.length()").value(5));
    }

    @Test
    void picks_premiumGroup_correctPriceAndCount() throws Exception {
        // 4 PREMIUM picks: extra full gutter clear, roof and exterior inspection,
        // smart-home package install, pre-winter full-home inspection
        mockMvc.perform(get(PICKS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.premium.aLaCartePriceCents").value(14900))
                .andExpect(jsonPath("$.premium.services.length()").value(4));
    }

    @Test
    void picks_standingItemsExcluded_fromMenu() throws Exception {
        // "Filter check/swap" is is_free_with_every_visit=true and must NOT appear in picks
        mockMvc.perform(get(PICKS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.basic.services[?(@.name == 'Filter check/swap')]").isEmpty());
    }

    @Test
    void picks_services_haveRequiredFields() throws Exception {
        mockMvc.perform(get(PICKS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.basic.services[0].id").isNumber())
                .andExpect(jsonPath("$.basic.services[0].name").isString())
                .andExpect(jsonPath("$.basic.services[0].category").isString())
                .andExpect(jsonPath("$.basic.services[0].aLaCartePriceCents").value(4900))
                .andExpect(jsonPath("$.basic.services[0].description").isString())
                .andExpect(jsonPath("$.basic.services[0].defaultDurationMinutes").isNumber());
    }

    // ── Security sanity: protected endpoint still requires auth ───────────────

    @Test
    void protectedEndpoint_me_stillRequiresAuth() throws Exception {
        // The catalog allowlist must not have over-opened other endpoints.
        mockMvc.perform(get(ME_URL))
                .andExpect(status().isUnauthorized());
    }

    // ── FoundingRateAvailability seam — default provider (slots open) ─────────

    @Test
    void plans_complete_foundingRateAvailable_whenDefaultProviderReturnsTrue() throws Exception {
        // DefaultFoundingRateAvailability returns true (0 founding subscribers < 15).
        // COMPLETE has a founding price seeded → foundingRateAvailable must be true.
        // foundingMonthlyPriceCents must still be 12900 regardless of slot availability.
        mockMvc.perform(get(PLANS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].code").value("COMPLETE"))
                .andExpect(jsonPath("$[1].foundingRateAvailable").value(true))
                .andExpect(jsonPath("$[1].foundingMonthlyPriceCents").value(12900));
    }

    // ── FoundingRateAvailability seam — slots exhausted (cap reached) ─────────

    /**
     * Verifies that the founding-rate seam is respected: when the provider reports
     * no slots remaining, COMPLETE's {@code foundingRateAvailable} flips to false,
     * but {@code foundingMonthlyPriceCents} stays 12900 (price is always returned).
     *
     * <p>Uses a separate Spring application context with an overriding bean so the
     * main test class can remain stateless.
     */
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @AutoConfigureMockMvc
    @Import({TestcontainersConfiguration.class, SlotsExhaustedFoundingConfig.class})
    static class WhenFoundingSlotsExhausted {

        @Autowired
        MockMvc mockMvc;

        @Test
        void plans_complete_foundingRateAvailable_isFalse_whenSlotsExhausted() throws Exception {
            mockMvc.perform(get("/api/catalog/plans"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[1].code").value("COMPLETE"))
                    .andExpect(jsonPath("$[1].foundingRateAvailable").value(false))
                    // Price is always returned — it's displayed as "if you had signed up early"
                    .andExpect(jsonPath("$[1].foundingMonthlyPriceCents").value(12900));
        }

        @Test
        void plans_essential_foundingRateAvailable_remainsFalse_whenSlotsExhausted() throws Exception {
            // ESSENTIAL has no founding price → false regardless of slot count
            mockMvc.perform(get("/api/catalog/plans"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].code").value("ESSENTIAL"))
                    .andExpect(jsonPath("$[0].foundingRateAvailable").value(false));
        }
    }
}
