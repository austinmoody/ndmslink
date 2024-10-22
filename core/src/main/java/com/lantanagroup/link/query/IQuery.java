package com.lantanagroup.link.query;

import com.lantanagroup.link.model.PatientOfInterestModel;
import com.lantanagroup.link.model.ReportContext;
import com.lantanagroup.link.model.ReportCriteria;
import org.springframework.context.ApplicationContext;

import java.util.List;

public interface IQuery {
  void execute(ReportCriteria criteria, ReportContext context, List<PatientOfInterestModel> patientIdentifiers, String reportId, List<String> resourceTypes, String measureId);
  void setApplicationContext(ApplicationContext context);
}
