package com.lantanagroup.link.cli.config;

import com.lantanagroup.link.config.api.ApiDataStoreConfig;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import javax.validation.constraints.NotNull;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "cli.store-epic-totals")
public class StoreEpicTotalsData {
    @NotNull
    private String profileUrl;

    @NotNull
    private String measureUrl;

    @NotNull
    private String subjectIdentifier;

    @NotNull
    private String measureReportId;

    @NotNull
    private ApiDataStoreConfig dataStore;
}
