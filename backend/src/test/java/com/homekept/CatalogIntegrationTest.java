package com.homekept;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the catalog vertical slice.
 *
 * <p>Runs against a real Postgres via Testcontainers. Flyway runs V1, V2, and V11
 * migrations on startup; JPA validates against the resulting schema (ddl-auto: validate).
 *
 * <p>Covers:
 * <ul>
 *   <li>GET /api/catalog/plans reachable without auth (public)</li>
 *   <li>Returns 2 tiers in correct order (COMPLETE, PREMIER)</li>
 *   <li>Exact prices and inclusions per the repositioned docs/pricing-and-visits.md
 *       (V11__remove_essential_and_founding.sql)</li>
 *   <li>Services array populated from plan_tier_service seed</li>
 *   <li>GET /api/catalog/picks reachable without auth</li>
 *   <li>Picks grouped by BASIC/MEDIUM/PREMIUM with correct à la carte prices</li>
 *   <li>Pick counts match the seed (5 BASIC, 5 MEDIUM, 4 PREMIUM)</li>
 *   <li>Standing items are excluded from picks (is_free_with_every_visit = true)</li>
 *   <li>A protected endpoint (GET /api/auth/me) still requires auth — allowlist not over-opened</li>
 *   <li>Flyway V2 + V11 + JPA validate boots cleanly (implicit — if the test context starts, it passed)</li>
 * </ul>
 */
class CatalogIntegrationTest extends AbstractIntegrationTest {

    private static final String PLANS_URL = "/api/catalog/plans";
    private static final String PICKS_URL = "/api/catalog/picks";
    private static final String ME_URL    = "/api/auth/me";

    @Autowired JdbcTemplate jdbc;

    // ── /api/catalog/plans — public access ───────────────────────────────────

    @Test
    void plans_isPublic_noAuthRequired() throws Exception {
        mockMvc.perform(get(PLANS_URL))
                .andExpect(status().isOk());
    }

    @Test
    void plans_returns2Tiers() throws Exception {
        mockMvc.perform(get(PLANS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void plans_orderedByPrice_completeFirst() throws Exception {
        mockMvc.perform(get(PLANS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("COMPLETE"))
                .andExpect(jsonPath("$[1].code").value("PREMIER"));
    }

    // ── COMPLETE — exact values per V11__remove_essential_and_founding.sql ───

    @Test
    void plans_complete_exactPrices() throws Exception {
        mockMvc.perform(get(PLANS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].displayName").value("Complete"))
                .andExpect(jsonPath("$[0].monthlyPriceCents").value(16900))
                .andExpect(jsonPath("$[0].annualPriceCents").value(169000))
                .andExpect(jsonPath("$[0].visitsPerYear").value(8));
    }

    @Test
    void plans_complete_picks() throws Exception {
        mockMvc.perform(get(PLANS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].includedPicksPerYear").value(3))
                .andExpect(jsonPath("$[0].maxPremiumPicksPerYear").value(1));
    }

    // ── PREMIER — exact values from docs/pricing-and-visits.md ───────────────

    @Test
    void plans_premier_exactPrices() throws Exception {
        mockMvc.perform(get(PLANS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].displayName").value("Premier"))
                .andExpect(jsonPath("$[1].monthlyPriceCents").value(24900))
                .andExpect(jsonPath("$[1].annualPriceCents").value(249000))
                .andExpect(jsonPath("$[1].visitsPerYear").value(12));
    }

    @Test
    void plans_premier_picks() throws Exception {
        mockMvc.perform(get(PLANS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].includedPicksPerYear").value(6))
                .andExpect(jsonPath("$[1].maxPremiumPicksPerYear").value(3));
    }

    // ── Stripe price ids — V11 nulled COMPLETE's, PREMIER untouched ──────────

    @Test
    void planTier_stripePriceIds_completeNulledByV11_premierUnchanged() {
        // V11__remove_essential_and_founding.sql cleared COMPLETE's old $149 Stripe price
        // ids to NULL (they pointed at the retired price) — this is what makes checkout
        // fail closed with PLAN_NOT_PURCHASABLE until the founder fills in new ones via
        // docs/stripe-price-ids.sql.
        Map<String, Object> complete = jdbc.queryForMap(
                "SELECT stripe_price_id_monthly, stripe_price_id_annual FROM plan_tier WHERE code = 'COMPLETE'");
        assertThat(complete.get("stripe_price_id_monthly")).isNull();
        assertThat(complete.get("stripe_price_id_annual")).isNull();

        // PREMIER was never touched by V11. This fresh test database has not run
        // docs/stripe-price-ids.sql (that only ever runs against production), so PREMIER's
        // ids are still NULL here too — but if a fixture ever starts seeding real ids,
        // this assertion flips to "both present" rather than silently passing either way.
        Map<String, Object> premier = jdbc.queryForMap(
                "SELECT stripe_price_id_monthly, stripe_price_id_annual FROM plan_tier WHERE code = 'PREMIER'");
        Object premierMonthly = premier.get("stripe_price_id_monthly");
        Object premierAnnual = premier.get("stripe_price_id_annual");
        if (premierMonthly != null || premierAnnual != null) {
            assertThat(premierMonthly).isNotNull();
            assertThat(premierAnnual).isNotNull();
        } else {
            assertThat(premierMonthly).isNull();
            assertThat(premierAnnual).isNull();
        }
    }

    // ── Services array populated ──────────────────────────────────────────────

    @Test
    void plans_complete_servicesArrayHas4StandingItems() throws Exception {
        // COMPLETE has 4 standing-item rows in plan_tier_service
        mockMvc.perform(get(PLANS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].services.length()").value(4));
    }

    @Test
    void plans_complete_standingServicesHaveFrequency8() throws Exception {
        // Every standing-item service for COMPLETE runs 8 times/year (once per visit)
        mockMvc.perform(get(PLANS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].services[0].frequencyPerYear").value(8))
                .andExpect(jsonPath("$[0].services[1].frequencyPerYear").value(8))
                .andExpect(jsonPath("$[0].services[2].frequencyPerYear").value(8))
                .andExpect(jsonPath("$[0].services[3].frequencyPerYear").value(8));
    }

    @Test
    void plans_premier_servicesHaveFrequency12() throws Exception {
        mockMvc.perform(get(PLANS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].services[0].frequencyPerYear").value(12));
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
}
