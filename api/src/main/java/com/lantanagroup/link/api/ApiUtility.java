package com.lantanagroup.link.api;

import com.lantanagroup.link.FhirDataProvider;
import com.lantanagroup.link.FhirHelper;
import com.lantanagroup.link.config.api.ApiDataStoreConfig;
import com.lantanagroup.link.model.ReportContext;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.Measure;

public class ApiUtility {

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

}
