package com.lantanagroup.link.config.api;

import lombok.Getter;
import lombok.Setter;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
public class EpicTotalsDataBundleConfig {
    private String bundleId;
    private String totalsReportId;
}
