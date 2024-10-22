package com.lantanagroup.link.ndms;

import com.lantanagroup.link.*;
import com.lantanagroup.link.Constants;
import com.lantanagroup.link.auth.LinkCredentials;
import com.lantanagroup.link.config.api.ApiConfig;
import com.lantanagroup.link.model.ReportContext;
import com.lantanagroup.link.model.ReportCriteria;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

@Component
public class NdmsMeasureReportGenerator implements IMeasureReportGenerator {
    private static final Logger logger = LoggerFactory.getLogger(NdmsMeasureReportGenerator.class);
    private final ApiConfig apiConfig;

    private CodeSystem trac2esCodeSystem = null;
    private final NdmsUtility ndmsUtility = new NdmsUtility();

    public NdmsMeasureReportGenerator(ApiConfig apiConfig) {
        this.apiConfig = apiConfig;
    }

    @Override
    public void generate(StopwatchManager stopwatchManager,
                         ReportContext reportContext,
                         ReportContext.MeasureContext measureContext,
                         ReportCriteria criteria,
                         ApiConfig config,
                         LinkCredentials user,
                         IReportAggregator reportAggregator)  throws ParseException, ExecutionException, InterruptedException, IOException {
        logger.info("Patient list is : {}", measureContext.getPatientsOfInterest().size());
        ForkJoinPool forkJoinPool = config.getMeasureEvaluationThreads() != null
                ? new ForkJoinPool(config.getMeasureEvaluationThreads())
                : ForkJoinPool.commonPool();

        try {

            //TODO: Remove this for DEBUG only
            if (apiConfig.getDebugOutput()) {
                writeLine(apiConfig.getDebugOutputEncounterFile(), "EncounterId,EncounterStatus,EncounterPeriodStart,EncounterPeriodEnd");
                writeLine(apiConfig.getDebugOutputLocationFile(), "locationId,encounterId,locationDisplay,locationPeriodStart,locationPeriodEnd");
                writeLine(apiConfig.getDebugOutputLocationAliasFile(), "randomId,locationId,encounterId,alias");
            }

            final Date startDate = Helper.parseFhirDate(criteria.getPeriodStart());
            final Date endDate = Helper.parseFhirDate(criteria.getPeriodEnd());

            // Read in NHSN bed list
            // TODO: Will revisit this is a bit of a mess to get initial Measures for comparison
            String nhsnCsvData = Files.readString(Path.of(config.getNhsnBedListCsvFile()));
            final List<NhsnLocation> nhsnLocations = NhsnLocation.parseCsvData(nhsnCsvData);

            List<MeasureReport> patientMeasureReports = forkJoinPool.submit(() ->
                    measureContext.getPatientsOfInterest().parallelStream().filter(patient -> !StringUtils.isEmpty(patient.getId())).map(patient -> {

                        logger.info("Generating measure report for patient {}", patient);
                        MeasureReport patientMeasureReport = new MeasureReport();
                        try {
                            String patientDataBundleId = ReportIdHelper.getPatientDataBundleId(reportContext.getMasterIdentifierValue(), patient.getId());

                            // get patient bundle from the fhirserver
                            FhirDataProvider fhirStoreProvider = new FhirDataProvider(config.getDataStore());
                            Bundle patientBundle = fhirStoreProvider.getBundleById(patientDataBundleId);

                            // TODO: Saving this to a CSV is a total Debug situation
                            if (apiConfig.getDebugOutput()) {
                                saveCsvDebug(patientBundle, apiConfig);
                            }

                            // Pull Location identifiers from Encounters if the Location has a period.start / period.end
                            // that falls in range of the passed in start/end dates when generating the reports.
                            List<String> relevantLocationIdentifiers = patientBundle.getEntry().stream()
                                    .map(Bundle.BundleEntryComponent::getResource)
                                    .filter(Encounter.class::isInstance)
                                    .map(Encounter.class::cast)
                                    .filter(encounter -> hasRelevantLocation(encounter, startDate, endDate))
                                    .flatMap(encounter -> encounter.getLocation().stream())
                                    .map(location -> location.getLocation().getReference())
                                    .filter(Objects::nonNull)  // remove nulls
                                    .filter(ref -> !ref.isEmpty())  // remove empty strings
                                    .distinct()
                                    .collect(Collectors.toList());

                            // Pull relevant Location resources using the list if ID's generated in previous step.
                            // Filter out any Location that has no aliases as those are used for finding the Nebraska
                            // Med bed code.
                            List<Location> relevantLocations = patientBundle.getEntry().stream()
                                    .map(Bundle.BundleEntryComponent::getResource)
                                    .filter(Location.class::isInstance)
                                    .map(Location.class::cast)
                                    .filter(location -> relevantLocationIdentifiers.contains("Location/" + location.getIdElement().getIdPart()))
                                    .filter(location -> location.hasAlias() && !location.getAlias().isEmpty())
                                    .collect(Collectors.toList());

                            for (Location location : relevantLocations) {
                                if (location.hasAlias()) {
                                    List<String> aliases = location.getAlias().stream()
                                            .map(StringType::getValue)
                                            .collect(Collectors.toList());

                                    Optional<NhsnLocation> nhsnLocation = getNhsnLocation(nhsnLocations, aliases);
                                    nhsnLocation.ifPresent(
                                            loc -> {

                                                // Lookup TRAC2ES Code
                                                // TODO: !!! Right now if we do not have a NebMed to TRAC2ES Map then we do not include
                                                //       Looking for feedback from NDMS re: this.  So this may change.
                                                CodeableConcept groupCodeableConcept = getTrac2esCodeableConcept(config.getEvaluationService(), config.getTrac2esCodeSystem(), loc.getTrac2es());

                                                if (groupCodeableConcept != null) {
                                                    MeasureReport.MeasureReportGroupComponent group = new MeasureReport.MeasureReportGroupComponent();
                                                    group.setCode(groupCodeableConcept);

                                                    MeasureReport.MeasureReportGroupPopulationComponent occupied = new MeasureReport.MeasureReportGroupPopulationComponent();
                                                    CodeableConcept populationOccupiedCodeableConcept = ndmsUtility.getOccPopulationCodeByTrac2es(config.getEvaluationService(), config.getTrac2esNdmsConceptMap(), loc.getTrac2es());
                                                    occupied.setCode(populationOccupiedCodeableConcept);
                                                    occupied.setCount(1);

                                                    group.addPopulation(occupied);

                                                    patientMeasureReport.addGroup(group);

                                                }
                                            }
                                    );
                                }
                            }

                        } catch (Exception ex) {
                            logger.error("Issue generating patient measure report for {}, error {}", patient, ex.getMessage());
                        }

                        String measureReportId = ReportIdHelper.getPatientMeasureReportId(measureContext.getReportId(), patient.getId());
                        patientMeasureReport.setId(measureReportId);
                        // Tag individual MeasureReport as patient-data as it references a patient and will be found for expunge
                        patientMeasureReport.getMeta().addTag(Constants.MainSystem, Constants.patientDataTag,"");

                        logger.info(String.format("Persisting patient %s measure report with id %s", patient, measureReportId));
                        Stopwatch stopwatch = stopwatchManager.start("store-measure-report");
                        reportContext.getFhirProvider().updateResource(patientMeasureReport);
                        stopwatch.stop();

                        // Add Location Info to MeasureReport
                        ndmsUtility.addLocationSubjectToMeasureReport(patientMeasureReport, reportContext.getReportLocation());

                        return patientMeasureReport;
                    }).collect(Collectors.toList())).get();
            // to avoid thread collision remove saving the patientMeasureReport on the FhirServer from the above parallelStream
            // pass them to aggregators using measureContext
            measureContext.setPatientReports(patientMeasureReports);
        } finally {
            if (forkJoinPool != null) {
                forkJoinPool.shutdown();
            }
        }
        MeasureReport masterMeasureReport = reportAggregator.generate(criteria, reportContext, measureContext, config);

        // Add  Organization Information to MeasureReport
        ndmsUtility.addLocationSubjectToMeasureReport(masterMeasureReport, reportContext.getReportLocation());

        // Tag "Master" MeasureReport
        masterMeasureReport.getMeta().addTag(Constants.NDMS_AGGREGATE_MEASURE_REPORT);

        measureContext.setMeasureReport(masterMeasureReport);

    }

    @Override
    public void store(ReportContext.MeasureContext measureContext, ReportContext reportContext) {
        measureContext.getPatientReports().forEach(report -> reportContext.getFhirProvider().updateResource(report));
        reportContext.getFhirProvider().updateResource(measureContext.getMeasureReport());
    }

    private static boolean hasRelevantLocation(Encounter encounter, Date startDate, Date endDate) {
        return encounter.getLocation().stream()
                .anyMatch(location -> isLocationRelevant(location, startDate, endDate));
    }

    private static boolean isLocationRelevant(Encounter.EncounterLocationComponent location, Date startDate, Date endDate) {
        if (location.getPeriod() == null) {
            return false; // If no period is specified, consider it NOT relevant
        }

        Date locationStart = location.getPeriod().getStart();
        Date locationEnd = location.getPeriod().getEnd();

        // Period exists but both start/stop don't exist consider NOT relevant
        if (locationStart == null && locationEnd == null) {
            return false;
        }

        return (locationStart == null || !locationStart.after(startDate)) &&
                (locationEnd == null || !locationEnd.before(endDate));
    }

    private Optional<NhsnLocation> getNhsnLocation(List<NhsnLocation> nhsnLocations, List<String> aliases) {

        // TODO: Need to verify this mapping w/ NDMS
        // At first I was looking first for one of the aliases in the "Unit Label" column.
        // Then for those returned columns further looking for one of the aliases in the
        // "Your Code" column.  Digging around in Bellevue data on 20-Oct-2024 it almost
        // appears that it could be an either or situation?  So the commented out code
        // below is from pre 20-October-2024.  Now going to try either Unit Label or
        // Your Code.
//        List<NhsnLocation> locationsByUnitLabel = nhsnLocations.stream()
//                .filter(location -> aliases.contains(location.getUnit()))
//                .collect(Collectors.toList());
//
//        if (!locationsByUnitLabel.isEmpty()) {
//            return locationsByUnitLabel.stream()
//                    .filter(location -> aliases.contains(location.getCode()))
//                    .findFirst();
//        }


        List<NhsnLocation> byUnit = nhsnLocations.stream()
                .filter(location -> aliases.contains(location.getUnit()))
                .collect(Collectors.toList());

        List<NhsnLocation> byCode = nhsnLocations.stream()
                .filter(location -> aliases.contains(location.getCode()))
                .collect(Collectors.toList());

        if (!byCode.isEmpty()) {
            return Optional.of(byCode.get(0));
        }

        if (!byUnit.isEmpty()) {
            return Optional.of(byUnit.get(0));
        }

        return Optional.empty();
    }

    private CodeableConcept getTrac2esCodeableConcept(String evaluationServiceLocation, String codeSystemLocation, String trac2esCode) {

        // Here we take the TRAC2ES code which we got from looking up the NDMS/BEL code
        // And lookup the full Coding information from the configured TRAC2ES CodeSystem
        // which we are going to assume is loaded on the CQF Evaluation server
        // REFACTOR

        // TODO - need a default "no map" concept.
        CodeableConcept codeableConcept = null;

        // Pull down the CodeSystem if necessary
        if ((trac2esCodeSystem == null) || trac2esCodeSystem.isEmpty()) {
            FhirDataProvider evaluationService = new FhirDataProvider(evaluationServiceLocation);
            trac2esCodeSystem = evaluationService.getCodeSystemById(codeSystemLocation);
        }

        for (CodeSystem.ConceptDefinitionComponent concept : trac2esCodeSystem.getConcept()) {
            if (concept.getCode().equals(trac2esCode)) {
                Coding coding = new Coding();
                coding.setCode(concept.getCode());
                coding.setDisplay(concept.getDisplay());
                coding.setSystem(trac2esCodeSystem.getUrl());
                codeableConcept = new CodeableConcept(coding);
                codeableConcept.addCoding(coding);
            }
        }

        return codeableConcept;
    }

    private void saveCsvDebug(Bundle patientBundle, ApiConfig apiConfig) {

        // Loop & Save Encounter Information
        for (Bundle.BundleEntryComponent bec : patientBundle.getEntry()) {
            if (bec.getResource() instanceof Encounter) {
                Encounter encounter = (Encounter) bec.getResource();
                String encounterPeriodStart = "";
                String encounterPeriodEnd = "";
                if (encounter.getPeriod() != null) {
                    if (encounter.getPeriod().getStart() != null) {
                        encounterPeriodStart = encounter.getPeriod().getStart().toString();
                    }

                    if (encounter.getPeriod().getEnd() != null) {
                        encounterPeriodEnd = encounter.getPeriod().getEnd().toString();
                    }
                }
                String encounterLine = String.format("%s,%s,%s,%s",encounter.getId(),
                        encounter.getStatus().name(),
                        encounterPeriodStart,
                        encounterPeriodEnd);
                writeLine(apiConfig.getDebugOutputEncounterFile(), encounterLine);

                // Loop Encounter Locations
                for (Encounter.EncounterLocationComponent elc : encounter.getLocation()) {
                    String locationPeriodStart = "";
                    String locationPeriodEnd = "";
                    if (elc.getPeriod() != null) {
                        if (elc.getPeriod().getStart() != null) {
                            locationPeriodStart = elc.getPeriod().getStart().toString();
                        }

                        if (elc.getPeriod().getEnd() != null) {
                            locationPeriodEnd = elc.getPeriod().getEnd().toString();
                        }
                    }

                    String locationLine = String.format("%s,%s,%s,%s,%s",
                            elc.getLocation().getReference(),
                            encounter.getId(),
                            elc.getLocation().getDisplay(),
                            locationPeriodStart,
                            locationPeriodEnd);
                    writeLine(apiConfig.getDebugOutputLocationFile(), locationLine);

                    // Pull Loop Alias
                    Optional<Location> aliasLocation = patientBundle
                            .getEntry()
                            .stream()
                            .map(Bundle.BundleEntryComponent::getResource)
                            .filter(Location.class::isInstance)
                            .map(Location.class::cast)
                            .filter(loc -> loc.getIdElement().getIdPart().equals(elc.getLocation().getReference().replaceAll("Location/","")))
                            .findAny();

                    if (aliasLocation.isPresent()) {

                        for (StringType alias : aliasLocation.get().getAlias()) {
                            String locationAliasLine = String.format("%s,%s,%s,%s",
                                    UUID.randomUUID().toString(),
                                    elc.getLocation().getReference(),
                                    encounter.getId(),
                                    alias.getValue()
                            );
                            writeLine(apiConfig.getDebugOutputLocationAliasFile(), locationAliasLine);
                        }
                    }
                }

            }
        }
    }

    private void writeLine(String fileName, String line) {
        try(FileWriter fw = new FileWriter(fileName, true);
            BufferedWriter bw = new BufferedWriter(fw);
            PrintWriter out = new PrintWriter(bw))
        {
            out.println(line);
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }

}
