package com.homekept.visit;

import com.homekept.catalog.PlanCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Spring Data repository for {@link VisitTemplate}.
 */
public interface VisitTemplateRepository extends JpaRepository<VisitTemplate, Long> {

    /**
     * Returns the templates whose {@code minTier} is at or below the subscriber's plan tier.
     * The calendar is cumulative: a COMPLETE subscriber gets ESSENTIAL + COMPLETE templates.
     *
     * <p>In-list is safe here because PlanCode has exactly 3 values and the list is
     * always built programmatically by {@link VisitSchedulingService}.
     */
    @Query("SELECT vt FROM VisitTemplate vt WHERE vt.minTier IN :tiers ORDER BY vt.month ASC")
    List<VisitTemplate> findByMinTierIn(@Param("tiers") List<PlanCode> tiers);
}
