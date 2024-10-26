package com.lantanagroup.link;

import com.lantanagroup.link.config.api.ApiConfig;
import com.lantanagroup.link.model.ReportContext;
import com.lantanagroup.link.model.ReportCriteria;

public interface IPatientOfInterest {
    void getPatientsOfInterest(ReportCriteria criteria, ReportContext context, ApiConfig config);
}
