package com.lantanagroup.link.config.api;

import com.lantanagroup.link.config.publisher.FhirPublisherConfig;
import lombok.Getter;
import lombok.Setter;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
public class ApiPublishers {
    private FhirPublisherConfig fhir;
    // For now, unfortunately, we would need to add new "publishers"
    // here and in the API configuration.
}
