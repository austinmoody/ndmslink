package com.lantanagroup.link.cli;

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import ca.uhn.fhir.rest.server.exceptions.ResourceGoneException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import com.lantanagroup.link.FhirDataProvider;
import com.lantanagroup.link.IdentifierHelper;
import com.lantanagroup.link.cli.config.StoreEpicTotalsData;
import com.lantanagroup.link.cli.models.EpicTotalDataSet;
import com.lantanagroup.link.config.api.ApiDataStoreConfig;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.MeasureReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/*
The purpose of this is to be able to store total beds for EPIC facilities as MeasureReport in the system.

EPIC systems, currently there are is no way to determine the total # of beds in a facility (overall or
by bed type).  From EPIC FHIR we can determine occupied beds (so for example there are 5 CC beds occupied) but
not how many CC beds there are total.

This just lets us specify this information and have a MeasureReport created.

Specifying this input:

CC,urn:trac2es:bed-types,numTotCCBeds,http://hl7.org/fhir/uv/saner/CodeSystem/MeasuredValues,5

Should generate this group:

{
  "code": {
    "coding": [
      {
        "code": "CC",
        "system": "urn:trac2es:bed-types"
      }
    ]
  },
  "population": [
    {
      "code": {
        "coding": [
          {
            "system": "http://hl7.org/fhir/uv/saner/CodeSystem/MeasuredValues",
            "code": "numTotCCBeds"
          }
        ]
      },
      "count": 5
    }
  ]
}
 */
@ShellComponent
public class StoreEpicTotalsDataManual extends BaseShellCommand {
    private static final Logger logger = LoggerFactory.getLogger(StoreEpicTotalsDataManual.class);
    private StoreEpicTotalsData config;

    @ShellMethod(
            key = "epic-totals-manual",
            value = "Specify totals for EPIC facilities to be stored for latter aggregation."
    )
    public void execute(
            @ShellOption(arity = 1, help = "Comma-separated sets of data. Each set should be in the format 'GroupCode,GroupSystem,PopulationCode,PopulationSystem,PopulationCount'. Use '|' to separate multiple sets.")
            String dataSets
    ) {

        config = applicationContext.getBean(StoreEpicTotalsData.class);

        String[] sets = dataSets.split("\\|");
        List<EpicTotalDataSet> parsedDataSets = new ArrayList<>();

        for (String set : sets) {
            parsedDataSets.add(parseDataSet(set));
        }

        // Pull back a report if it exists with the id
        MeasureReport measureReport = getExistingMeasureReport();

        if (measureReport == null) {
            measureReport = createBaseMeasureReport();
        }

        measureReport = updateMeasureReportGroups(measureReport, parsedDataSets);

        submit(measureReport);
    }

    public MeasureReport createBaseMeasureReport() {
        Date date = new Date();

        MeasureReport measureReport = new MeasureReport();
        measureReport.getMeta().addProfile(config.getProfileUrl());
        measureReport.setStatus(MeasureReport.MeasureReportStatus.COMPLETE);
        measureReport.setType(MeasureReport.MeasureReportType.SUMMARY);
        measureReport.setMeasure(config.getMeasureUrl());
        measureReport.getSubject().setIdentifier(IdentifierHelper.fromString(config.getSubjectIdentifier()));
        measureReport.setDate(date);
        measureReport.getPeriod()
                .setStart(date, TemporalPrecisionEnum.DAY)
                .setEnd(date, TemporalPrecisionEnum.DAY);
        return measureReport;
    }

    public MeasureReport updateMeasureReportGroups(MeasureReport measureReport, List<EpicTotalDataSet> dataSets) {

        for (EpicTotalDataSet dataSet : dataSets) {

            if (!groupExists(measureReport, dataSet)) {
                measureReport.addGroup(dataSet.toMeasureReportGroupComponent());
                // Go ahead and return here because if the group didn't exist then we have
                // added the Group and corresponding Population so there is no need to go
                // forwarded and update the Population.
                return measureReport;
            }

            for (MeasureReport.MeasureReportGroupComponent group : measureReport.getGroup()) {
                if (populationExistsInGroup(group, dataSet)) {
                    updateGroup(group, dataSet);
                } else {
                    group.addPopulation(dataSet.toMeasureReportGroupPopulationComponent());
                }
            }
        }

        return measureReport;
    }

    private EpicTotalDataSet parseDataSet(String set) {
        String[] elements = set.split(",");
        if (elements.length != 5) {
            throw new IllegalArgumentException("Each data set must have exactly 5 elements. Found " + elements.length + " elements in set: " + set);
        }

        String groupCode = elements[0].trim();
        String groupSystem = elements[1].trim();
        String populationCode = elements[2].trim();
        String populationSystem = elements[3].trim();
        int populationCount;
        try {
            populationCount = Integer.parseInt(elements[4].trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Population count must be an integer. Error in set: " + set);
        }

        return new EpicTotalDataSet(groupCode, groupSystem, populationCode, populationSystem, populationCount);
    }

    private MeasureReport getExistingMeasureReport() {
        ApiDataStoreConfig dataStoreConfig = config.getDataStore();
        FhirDataProvider fhirDataProvider = new FhirDataProvider(dataStoreConfig);

        try {
            return fhirDataProvider.getMeasureReportById(config.getMeasureReportId());
        } catch (ResourceGoneException | ResourceNotFoundException ex) {
            // If there was a copy of the MeasureReport out there with the same ID previously, which was
            // deleted but not "expunged" you will get a HTTP 410 GONE code back from the FHIR Server.
            // We don't care about that because if everything else goes well we will re-create and store
            // the report.
            // Also, if there is no existing report we'll get a 404 NOT FOUND.  Also don't care about that.
            return null;
        }
    }

    private void submit(MeasureReport report) {

        ApiDataStoreConfig dataStoreConfig = config.getDataStore();
        FhirDataProvider fhirDataProvider = new FhirDataProvider(dataStoreConfig);
        String submissionUrl = String.format("MeasureReport/%s", config.getMeasureReportId());

        logger.info("Submitting MeasureReport to {}", submissionUrl);

        Bundle updateBundle = new Bundle();
        updateBundle.setType(Bundle.BundleType.TRANSACTION);
        updateBundle.addEntry()
                .setResource(report)
                .setRequest(new Bundle.BundleEntryRequestComponent()
                        .setMethod(Bundle.HTTPVerb.PUT)
                        .setUrl(submissionUrl));

        fhirDataProvider.transaction(updateBundle);
    }

    private boolean populationExistsInGroup(MeasureReport.MeasureReportGroupComponent group, EpicTotalDataSet dataSet) {
        boolean populationExists = false;
        for (MeasureReport.MeasureReportGroupPopulationComponent population : group.getPopulation()) {
            CodeableConcept populationCodeableConcept = population.getCode();
            for (Coding populationCoding : populationCodeableConcept.getCoding()) {
                if (populationCoding.getSystem().equals(dataSet.getPopulationSystem()) && populationCoding.getCode().equals(dataSet.getPopulationCode())) {
                    populationExists = true;
                    population.setCount(dataSet.getPopulationCount());
                }
            }
        }

        return populationExists;
    }

    private boolean groupExists(MeasureReport measureReport, EpicTotalDataSet dataSet) {
        boolean groupExists = false;
        for (MeasureReport.MeasureReportGroupComponent group : measureReport.getGroup()) {
            CodeableConcept groupCodeableConcept = group.getCode();
            for (Coding groupCoding : groupCodeableConcept.getCoding()) {
                if (groupCoding.getSystem().equals(dataSet.getGroupSystem()) && groupCoding.getCode().equals(dataSet.getGroupCode())) {
                    groupExists = true;
                }
            }
        }

        return groupExists;
    }

    private void updateGroup(MeasureReport.MeasureReportGroupComponent group, EpicTotalDataSet dataSet) {
        for (MeasureReport.MeasureReportGroupPopulationComponent population : group.getPopulation()) {
            CodeableConcept populationCodeableConcept = population.getCode();
            for (Coding populationCoding : populationCodeableConcept.getCoding()) {
                if (populationCoding.getSystem().equals(dataSet.getPopulationSystem()) && populationCoding.getCode().equals(dataSet.getPopulationCode())) {
                    population.setCount(dataSet.getPopulationCount());
                }
            }
        }
    }
}
