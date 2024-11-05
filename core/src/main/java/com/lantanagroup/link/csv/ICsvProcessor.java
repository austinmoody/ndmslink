package com.lantanagroup.link.csv;

import com.lantanagroup.link.config.api.ApiDataStoreConfig;
import com.lantanagroup.link.config.api.CsvProcessor;
import com.lantanagroup.link.model.ReportCriteria;
import com.opencsv.exceptions.CsvValidationException;
import org.hl7.fhir.r4.model.MeasureReport;

import java.io.IOException;
import java.text.ParseException;
import java.util.List;

public interface ICsvProcessor {
    // The CSV Processor will return a List of MeasureReports.  This is because one CSV may contain data for
    // more than one Location (Methodist for example).
    List<MeasureReport> process(ApiDataStoreConfig dataStoreConfig, String evaluationServiceConfig, ReportCriteria reportCriteria, CsvProcessor csvProcessorConfig, String csvContent) throws IOException, CsvValidationException, ParseException;
}
