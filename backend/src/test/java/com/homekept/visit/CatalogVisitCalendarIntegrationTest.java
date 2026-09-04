package com.homekept.visit;

import com.homekept.AbstractIntegrationTest;
import com.homekept.catalog.PlanCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the visits-per-plan invariant: the number of visits a subscriber gets must match the
 * plan they pay for.
 *
 * <p>Two independent sources currently decide this and nothing checks that they agree:
 * <ul>
 *   <li>{@code plan_tier.visits_per_year} — what the pricing page advertises.</li>
 *   <li>The seeded {@code visit_template} rows ({@code month} + {@code min_tier}) combined
 *       with {@link VisitSchedulingService#eligibleTiersFor} — what actually gets
 *       scheduled.</li>
 * </ul>
 *
 * <p>For every non-archived row in {@code plan_tier}, this asserts that the count of
 * {@code visit_template} rows whose {@code min_tier} is in
 * {@link VisitSchedulingService#eligibleTiersFor} for that plan's code equals that tier's
 * {@code visits_per_year}. Both sides are driven from the database (never a hardcoded tier
 * list), so this keeps holding as tiers and templates change — including after the pending
 * V12 migration that retires ESSENTIAL and re-tiers its four ESSENTIAL-min-tier templates
 * onto COMPLETE: COMPLETE's eligible-template count goes from (4 ESSENTIAL + 4 COMPLETE) = 8
 * to (0 ESSENTIAL + 8 COMPLETE) = 8 — unchanged — and PREMIER's stays (0 + 8 + 4) = 12 either
 * way, matching {@code plan_tier.visits_per_year} of 8 and 12 respectively.
 *
 * <p>Archived tiers ({@code archived_at IS NOT NULL}) are excluded: a retired tier is no
 * longer sold, so its calendar mapping is no longer a live commercial promise and isn't
 * expected to still line up with the current template set.
 */
class CatalogVisitCalendarIntegrationTest extends AbstractIntegrationTest {

    @Autowired VisitTemplateRepository visitTemplateRepository;

    @Test
    void everyPlanTierVisitsPerYearMatchesEligibleTemplateCount() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT code, visits_per_year FROM plan_tier WHERE archived_at IS NULL");

        // Sanity: the invariant is vacuously true (and useless) if there's nothing to check.
        assertThat(rows).isNotEmpty();

        for (Map<String, Object> row : rows) {
            String code = (String) row.get("code");
            int visitsPerYear = ((Number) row.get("visits_per_year")).intValue();

            PlanCode planCode = PlanCode.valueOf(code);
            List<PlanCode> eligibleTiers = VisitSchedulingService.eligibleTiersFor(planCode);
            int templateCount = visitTemplateRepository.findByMinTierIn(eligibleTiers).size();

            assertThat(templateCount)
                    .as("plan_tier %s: visits_per_year (%d) must equal the count of visit_template "
                            + "rows whose min_tier is in the eligible set %s (was %d)",
                            code, visitsPerYear, eligibleTiers, templateCount)
                    .isEqualTo(visitsPerYear);
        }
    }
}
