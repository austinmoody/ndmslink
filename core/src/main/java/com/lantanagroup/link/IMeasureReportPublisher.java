package com.lantanagroup.link;

import org.hl7.fhir.r4.model.MeasureReport;

public interface IMeasureReportPublisher<T> {
    void publish(T config, MeasureReport report);
}
