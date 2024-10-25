package com.lantanagroup.link.api;

import com.lantanagroup.link.FhirDataProvider;
import com.lantanagroup.link.FhirHelper;
import com.lantanagroup.link.config.api.ApiConfig;
import com.lantanagroup.link.config.api.ApiDataStoreConfig;
import com.lantanagroup.link.config.api.GenerateReportConfig;
import com.lantanagroup.link.model.ReportContext;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.Optional;

public class ApiUtility {

    private static final Logger logger = LoggerFactory.getLogger(ApiUtility.class);

    private ApiUtility() {
        throw new IllegalStateException("Utility class");
    }

    public static Location getAndVerifyLocation(String locationId, ApiDataStoreConfig dataStoreConfig) {
        FhirDataProvider dataStore = new FhirDataProvider(dataStoreConfig);
        Location location = dataStore.getLocationById(locationId);

        // Verify that the location has position information
        if (location.getPosition() == null) {
            throw new FHIRException(String.format("Location %s, specified for report generation does not include necessary geolocation", locationId));
        }

        if ( (location.getPosition().getLatitude() == null) || (location.getPosition().getLongitude() == null) ) {
            throw new FHIRException(String.format("Location %s, specified for report generation does not include necessary geolocation", locationId));
        }

        return location;
    }

    public static ReportContext.MeasureContext getAndVerifyMeasure(String measureId, String evaluationService) throws Exception {
        // Pull the report definition bundle from CQF (eval service)
        FhirDataProvider evaluationProvider = new FhirDataProvider(evaluationService);
        Bundle reportDefBundle = evaluationProvider.getBundleById(measureId);

        // Create & Return MeasureContext
        ReportContext.MeasureContext measureContext = new ReportContext.MeasureContext();
        measureContext.setReportDefBundle(reportDefBundle);
        measureContext.setBundleId(reportDefBundle.getIdElement().getIdPart());
        Measure measure = FhirHelper.getMeasure(reportDefBundle);
        measureContext.setMeasure(measure);

        return measureContext;

    }

    public static void addNoteToTask(Task task, String note) {
        task.addNote(
                new Annotation()
                        .setText(note)
                        .setTime(new Date())
        );
    }

    public static String getReportAggregatorClassName(ApiConfig config, String locationId) {
        String reportAggregatorClassName;

        Optional<GenerateReportConfig> generateReportConfig = config.getGenerateReportConfiguration().stream().filter(
                grc -> grc.getLocationId().equals(locationId)
        ).findFirst();

        if (generateReportConfig.isPresent() && !StringUtils.isEmpty(generateReportConfig.get().getReportAggregator())) {
            reportAggregatorClassName = generateReportConfig.get().getReportAggregator();
            logger.info("Using report aggregator class {}", reportAggregatorClassName);
        } else {
            throw new IllegalStateException("Report Aggregator class not found for location " + locationId);
        }

        return reportAggregatorClassName;
    }

    public static String getReportGeneratorClassName(ApiConfig config, String locationId) {
        String reportGeneratorClassName;

        Optional<GenerateReportConfig> generateReportConfig = config.getGenerateReportConfiguration().stream().filter(
                grc -> grc.getLocationId().equals(locationId)
        ).findFirst();

        if (generateReportConfig.isPresent() && !StringUtils.isEmpty(generateReportConfig.get().getReportGenerator())) {
            reportGeneratorClassName = generateReportConfig.get().getReportGenerator();
            logger.info("Using report generator class {}", reportGeneratorClassName);
        } else {
            throw new IllegalStateException("Report Generator class not found for location " + locationId);
        }

        return reportGeneratorClassName;
    }

    public static Location getLocationFromDataStore(ApiDataStoreConfig dataStoreConfig, String locationId) {
        FhirDataProvider dataStore = new FhirDataProvider(dataStoreConfig);
        return dataStore.getLocationById(locationId);
    }

    public static boolean locationHasPosition(Location location) {
        // Verify that the location has position information
        if (location.getPosition() == null) {
            return false;
        }

        return (location.getPosition().getLatitude() != null) && (location.getPosition().getLongitude() != null);
    }
}
