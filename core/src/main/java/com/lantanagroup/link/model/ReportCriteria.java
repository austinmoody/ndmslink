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

  public ReportCriteria(Collection<String> bundleIds, String periodStart, String periodEnd) {
    this.bundleIds = new TreeSet<>(bundleIds);
    this.periodStart = periodStart;
    this.periodEnd = periodEnd;
  }

  public Annotation getAnnotation() {
    Annotation annotation = new Annotation();
    annotation.setTime(new Date());
    annotation.setText(String.format("ReportCriteria parameters: periodStart - %s / periodEnd - %s / bundleIds - %s",
            this.getPeriodStart(),
            this.getPeriodEnd(),
            String.join(",", this.getBundleIds())));
    return annotation;
  }
}
