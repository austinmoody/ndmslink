package com.lantanagroup.link.config.api;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PatientListPullConfig {
    private String patientListLocation;
    private String patientListIdentifier;
    private PatientListReportingPeriods patientListReportingPeriod;
}
