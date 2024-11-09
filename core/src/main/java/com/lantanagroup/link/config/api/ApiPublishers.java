package com.lantanagroup.link.config.api;

import com.lantanagroup.link.config.publisher.FhirPublisherConfig;
import lombok.Getter;
import lombok.Setter;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Field;

@Getter
@Setter
@Configuration
public class ApiPublishers {
    private FhirPublisherConfig fhir;
    // For now, unfortunately, we would need to add new "publishers"
    // here and in the API configuration.

    public boolean publisherConfigured(String publisherName) {
        for (Field field : this.getClass().getDeclaredFields()) {
            if (field.getName().equals(publisherName)) {
                return true;
            }
        }
        return false;
    }
}
