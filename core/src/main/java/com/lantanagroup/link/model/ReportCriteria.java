package com.lantanagroup.link.model;

import lombok.Getter;
import org.hl7.fhir.r4.model.Annotation;

import java.util.*;

@Getter
public class ReportCriteria {
  private final SortedSet<String> bundleIds;
  private final String periodStart;
  private final String periodEnd;
  private final String locationId;
  private final String measureId;

  public ReportCriteria(Collection<String> bundleIds, String locationId, String measureId, String periodStart, String periodEnd) {
    this.bundleIds = new TreeSet<>(bundleIds);
    this.periodStart = periodStart;
    this.periodEnd = periodEnd;
    this.locationId = locationId;
    this.measureId = measureId;
  }

  public ReportCriteria(GenerateReport generateReport) {
    this.periodStart = generateReport.getPeriodStart();
    this.periodEnd = generateReport.getPeriodEnd();
    this.locationId = generateReport.getLocationId();
    this.measureId = generateReport.getMeasureId();
    // TODO: Remove these bundleIds when refactor complete
    this.bundleIds = new TreeSet<>();
  }

  public Annotation getAnnotation() {
    Annotation annotation = new Annotation();
    annotation.setTime(new Date());
    annotation.setText(String.format("ReportCriteria parameters: Location: %s / Measure: %s / periodStart: %s / periodEnd: %s / bundleIds: %s",
            this.locationId,
            this.measureId,
            this.getPeriodStart(),
            this.getPeriodEnd(),
            String.join(",", this.getBundleIds())));
    return annotation;
  }
}
