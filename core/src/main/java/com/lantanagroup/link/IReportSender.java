package com.lantanagroup.link;

import com.lantanagroup.link.config.bundler.BundlerConfig;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.MeasureReport;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

public interface IReportSender {
  String send(List<MeasureReport> masterMeasureReports, DocumentReference documentReference, HttpServletRequest request, FhirDataProvider fhirDataProvider, BundlerConfig bundlerConfig) throws Exception;
}
