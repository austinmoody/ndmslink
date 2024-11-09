package com.lantanagroup.link;

import com.lantanagroup.link.config.publisher.PublisherOutcome;
import org.hl7.fhir.r4.model.MeasureReport;

public interface IMeasureReportPublisher<T> {
    PublisherOutcome publish(T config, MeasureReport report);
}
