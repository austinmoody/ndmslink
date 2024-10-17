package com.lantanagroup.link;

import com.lantanagroup.link.config.api.ApiConfig;
import com.lantanagroup.link.model.ReportContext;
import com.lantanagroup.link.model.ReportCriteria;
import org.hl7.fhir.r4.model.MeasureReport;

import java.text.ParseException;
import java.util.concurrent.ExecutionException;

public interface IReportAggregator {
  MeasureReport generate(ReportCriteria criteria, ReportContext reportContext, ReportContext.MeasureContext measureContext, ApiConfig apiConfig) throws ParseException, ExecutionException;
}
