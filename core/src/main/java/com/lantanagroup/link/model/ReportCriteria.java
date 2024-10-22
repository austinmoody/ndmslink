package com.lantanagroup.link.model;

import lombok.Getter;
import org.hl7.fhir.r4.model.Annotation;

import java.util.Collection;
import java.util.Date;
import java.util.SortedSet;
import java.util.TreeSet;

@Getter
public class ReportCriteria {
  private final SortedSet<String> bundleIds;
  private final String periodStart;
  private final String periodEnd;
  private final String locationId;

  public ReportCriteria(Collection<String> bundleIds, String locationId, String periodStart, String periodEnd) {
    this.bundleIds = new TreeSet<>(bundleIds);
    this.periodStart = periodStart;
    this.periodEnd = periodEnd;
    this.locationId = locationId;
  }

  public Annotation getAnnotation() {
    Annotation annotation = new Annotation();
    annotation.setTime(new Date());
    annotation.setText(String.format("ReportCriteria parameters: Location: %s / periodStart: %s / periodEnd: %s / bundleIds: %s",
            this.locationId,
            this.getPeriodStart(),
            this.getPeriodEnd(),
            String.join(",", this.getBundleIds())));
    return annotation;
  }
}
