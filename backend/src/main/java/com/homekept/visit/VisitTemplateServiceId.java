package com.homekept.visit;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key for {@link VisitTemplateService}.
 */
public class VisitTemplateServiceId implements Serializable {

    private Long visitTemplate;
    private Long service;

    public VisitTemplateServiceId() {}

    public VisitTemplateServiceId(Long visitTemplate, Long service) {
        this.visitTemplate = visitTemplate;
        this.service = service;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VisitTemplateServiceId that)) return false;
        return Objects.equals(visitTemplate, that.visitTemplate)
                && Objects.equals(service, that.service);
    }

    @Override
    public int hashCode() {
        return Objects.hash(visitTemplate, service);
    }
}
