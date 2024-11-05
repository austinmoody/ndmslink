package com.lantanagroup.link.config.api;

import lombok.Getter;
import lombok.Setter;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@Configuration
@Validated
public class CsvProcessor {
    private String locationId;
    private String csvProcessorClass;
    private String measure;
    private Map<String, String> options = new HashMap<>();
}
