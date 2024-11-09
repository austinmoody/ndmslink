package com.lantanagroup.link.config.publisher;

import lombok.Getter;
import lombok.Setter;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@Configuration
public class FhirPublisherConfig {
    private String url;
    private String publisher;
    private FhirPublisherAuthType authType;
    private Map<String, String> authOptions = new HashMap<>();
}
