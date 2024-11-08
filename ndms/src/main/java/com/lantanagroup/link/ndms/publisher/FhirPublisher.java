package com.lantanagroup.link.ndms.publisher;

import com.lantanagroup.link.IMeasureReportPublisher;
import com.lantanagroup.link.config.publisher.FhirPublisherConfig;
import org.hl7.fhir.r4.model.MeasureReport;
import org.springframework.stereotype.Component;

@Component
public class FhirPublisher implements IMeasureReportPublisher<FhirPublisherConfig> {
    @Override
    public void publish(FhirPublisherConfig config, MeasureReport report) {
        // TODO
    }
}
