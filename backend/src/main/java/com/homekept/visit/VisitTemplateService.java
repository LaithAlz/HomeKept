package com.homekept.visit;

import com.homekept.catalog.Service;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Join entity linking a {@link VisitTemplate} to the {@link Service} rows
 * it includes, with ordering.
 *
 * <p>The 4 standing items (is_free_with_every_visit = true) are seeded into every
 * template via V6__visit.sql. Seasonal-focus services are not yet discrete catalog
 * services — they are captured in the template description and will be enriched in a
 * follow-up migration.
 */
@Entity
@Table(name = "visit_template_service")
@IdClass(VisitTemplateServiceId.class)
public class VisitTemplateService {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "visit_template_id", nullable = false)
    private VisitTemplate visitTemplate;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private Service service;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected VisitTemplateService() {}

    // ── Getters ───────────────────────────────────────────────────────────────

    public VisitTemplate getVisitTemplate() { return visitTemplate; }
    public Service getService() { return service; }
    public int getSortOrder() { return sortOrder; }
}
