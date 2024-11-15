package com.lantanagroup.link.ndms.methodist;

import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import com.lantanagroup.link.FhirDataProvider;
import com.lantanagroup.link.Helper;
import com.lantanagroup.link.ReportIdHelper;
import com.lantanagroup.link.config.api.ApiDataStoreConfig;
import com.lantanagroup.link.config.api.CsvProcessor;
import com.lantanagroup.link.csv.ICsvProcessor;
import com.lantanagroup.link.model.ReportCriteria;
import com.lantanagroup.link.ndms.MeasureReportSort;
import com.lantanagroup.link.ndms.NdmsConstants;
import com.lantanagroup.link.ndms.NdmsUtility;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.bean.HeaderColumnNameMappingStrategy;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.text.ParseException;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class MethodistCsvProcessor implements ICsvProcessor {
    private static final Logger logger = LoggerFactory.getLogger(MethodistCsvProcessor.class);
    private FhirDataProvider fhirDataProvider;
    private FhirDataProvider fhirEvalService;
    private List<String> removeBedTypes;
    private List<String> occupiedBedTypes;
    private List<String> availableBedTypes;
    private CsvProcessor csvProcessorConfig;
    private String targetBedTypesCodeSystem;
    private String targetNdmsConceptMap;
    private String evaluationServiceConfig;
    private final NdmsUtility ndmsUtility = new NdmsUtility();

    @Override
    public List<MeasureReport> process(ApiDataStoreConfig dataStoreConfig, String evaluationServiceConfig, ReportCriteria reportCriteria, CsvProcessor csvProcessorConfig, String csvContent) throws ResourceNotFoundException {

        List<MeasureReport> measureReports = new ArrayList<>();

        this.fhirDataProvider = new FhirDataProvider(dataStoreConfig);
        this.fhirEvalService = new FhirDataProvider(evaluationServiceConfig);
        this.csvProcessorConfig = csvProcessorConfig;
        this.evaluationServiceConfig = evaluationServiceConfig;

        verifyAndProcessOptions();

        // Read CSV into a list of the Methodist data model
        StringReader reader = new StringReader(csvContent);
        HeaderColumnNameMappingStrategy<MethodistDataModel> strategy =
                new HeaderColumnNameMappingStrategy<>();
        strategy.setType(MethodistDataModel.class);

        CsvToBean<MethodistDataModel> csvToBean = new CsvToBeanBuilder<MethodistDataModel>(reader)
                .withMappingStrategy(strategy)
                .withIgnoreLeadingWhiteSpace(true)
                .build();

        // Parse the entire CSV
        // We will loop each facility but, obviously, only work on active beds
        // So we may end up with some MeasureReports for facilities w/o any
        // totals if the beds are inactive for NDMS.
        List<MethodistDataModel> methodistDataModelList = csvToBean.parse();

        logger.info("Methodist CSV has {} entries", methodistDataModelList.size());

        /*
            These are the Bed Status types for Methodist
            AVAILABLE
            BLOCKED
            CLEANING
            DIRTY
            OCCUPIED
            OUT_OF_SERVICE

            Do Not Count In Any Total:
            BLOCKED
            OUT_OF_SERVICE

            Count These Type To See Occupied:
            OCCUPIED

            Count These Type To See Available:
            AVAILABLE
            CLEANING
            DIRTY

         */

        // Will need total beds by type (not counting BLOCKED & OUT_OF_SERVICE)
        // Will need total beds by facility (not counting BLOCKED & OUT_OF_SERVICE)
        // Will need occupied beds by type
        // Can calculate the rest.
        methodistDataModelList.stream()
                .collect(Collectors.groupingBy(MethodistDataModel::getFacility))
                .forEach((facility, dataList) -> {
                    Location location = getLocationByName(facility);
                    // Pull out location's active beds
                    List<MethodistDataModel> activeBeds = dataList.stream()
                            .filter(
                                    bed -> bed.getActive().equalsIgnoreCase("ACTIVE")
                            ).collect(Collectors.toList());
                    try {
                        measureReports.add(generateMeasureReportForFacility(reportCriteria, location, activeBeds));
                    } catch (ParseException e) {
                        throw new InternalErrorException(e);
                    }
                });

        return measureReports;
    }

    private void verifyAndProcessOptions() {

        final String errorTemplate = "CSV Processor not configured with '%s' option";

        if (this.csvProcessorConfig == null) {
            throw new InternalErrorException("CSV Processor not configured");
        }

        if (this.csvProcessorConfig.getOptions().isEmpty()) {
            throw new InternalErrorException("CSV Processor not configured with options");
        }

        // target-bed-types-code-system needs to exist in CsvProcessor options
        // to point to the CodeSystem on Eval/CQF Server that holds TRAC2ES codes
        // as TRAC2ES are the target bed types we are mapping to
        String codeSystemOptionName = "target-bed-types-code-system";
        if (this.csvProcessorConfig.getOptions().get(codeSystemOptionName) == null) {
            throw new InternalErrorException(
                    String.format(errorTemplate, codeSystemOptionName)
            );
        }
        this.targetBedTypesCodeSystem = this.csvProcessorConfig.getOptions().get(codeSystemOptionName);

        // target-to-ndms-concept-map needs to exist in CsvProcessor options
        // to point to the ConceptMap that is used to map between TRAC2ES codes (the target)
        // and the different Population codes for Available, Occupied, and Total.
        String conceptMapOptionName = "target-to-ndms-concept-map";
        if (this.csvProcessorConfig.getOptions().get(conceptMapOptionName) == null) {
            throw new InternalErrorException(
                    String.format(errorTemplate, conceptMapOptionName)
            );
        }
        this.targetNdmsConceptMap = this.csvProcessorConfig.getOptions().get(conceptMapOptionName);

        String removeBedTypesOptionName = "remove-bed-types";
        if (this.csvProcessorConfig.getOptions().get(removeBedTypesOptionName) == null) {
            throw new InternalErrorException(
                    String.format(errorTemplate, removeBedTypesOptionName)
            );
        }
        this.removeBedTypes = Arrays.stream(
                csvProcessorConfig.getOptions().get(removeBedTypesOptionName).split(",")
                )
                .map(String::trim)
                .collect(Collectors.toList());

        String occupiedBedTypesOptionName = "occupied-bed-types";
        if (this.csvProcessorConfig.getOptions().get(occupiedBedTypesOptionName) == null) {
            throw new InternalErrorException(
                    String.format(errorTemplate, occupiedBedTypesOptionName)
            );
        }
        this.occupiedBedTypes = Arrays.stream(
                        csvProcessorConfig.getOptions().get(occupiedBedTypesOptionName).split(",")
                )
                .map(String::trim)
                .collect(Collectors.toList());

        String availableBedTypesOptionName = "available-bed-types";
        if (this.csvProcessorConfig.getOptions().get(availableBedTypesOptionName) == null) {
            throw new InternalErrorException(
                    String.format(errorTemplate, availableBedTypesOptionName)
            );
        }
        this.availableBedTypes = Arrays.stream(
                        csvProcessorConfig.getOptions().get(availableBedTypesOptionName).split(",")
                )
                .map(String::trim)
                .collect(Collectors.toList());
    }

    private Location getLocationByName(String locationName) throws ResourceNotFoundException {
        String hashedLocationName = ReportIdHelper.hash(locationName);
        try {
            Bundle locationBundle = fhirDataProvider.findLocationByIdentifierValue(hashedLocationName);

            if (locationBundle.getEntry().isEmpty()) {
                throw new ResourceNotFoundException(locationName);
            }

            if (locationBundle.getEntry().size() > 1) {
                throw new InternalErrorException(
                        String.format("Multiple locations found for name '%s' (hashed id '%s')", locationName, hashedLocationName)
                );
            }

            return (Location) locationBundle.getEntry().get(0).getResource();
        } catch (ResourceNotFoundException e) {
            // Returning error to caller with Hashed ID may make no sense.
            throw new ResourceNotFoundException(
                    String.format("Location for name '%s' not found (hashed id '%s')", locationName, hashedLocationName)
            );
        } catch (InternalErrorException e) {
            throw new ResourceNotFoundException(e.getMessage());
        }
    }

    private MeasureReport generateMeasureReportForFacility(ReportCriteria reportCriteria, Location location, List<MethodistDataModel> locationData) throws ParseException {

        // It is assumed that the locationData is ONLY for the location.  But we'll want to check

        MeasureReport measureReport = new MeasureReport();

        // Master Identifier Value - in the EPIC FHIR calculation gets set in MeasureContext that we aren't using here.
        String masterIdentifierValue = ReportIdHelper.getMasterIdentifierValue(location.getId(), reportCriteria.getMeasureId(), reportCriteria.getPeriodStart(), reportCriteria.getPeriodEnd());
        // The id for the master measure report typically (in the EPIC FHIR calculation) comes from the masterIdentifierValue + the bundle id.
        // No bundle id's here so I'm using the location id.
        String masterMeasureReportIdentifier = ReportIdHelper.getMasterMeasureReportId(masterIdentifierValue, location.getId());

        measureReport.setId(masterMeasureReportIdentifier);
        measureReport.setType(MeasureReport.MeasureReportType.SUMMARY);
        measureReport.setStatus(MeasureReport.MeasureReportStatus.COMPLETE);
        measureReport.setPeriod(new Period());
        measureReport.getPeriod().setStart(Helper.parseFhirDate(reportCriteria.getPeriodStart()));
        measureReport.getPeriod().setEnd(Helper.parseFhirDate(reportCriteria.getPeriodEnd()));

        // Assumption is that the full URL to the Measure will be provided in the configuration
        // not bothering to pull as we aren't actually doing any CQF stuff here.
        measureReport.setMeasure(reportCriteria.getMeasureId());

        // Let's add the group & counts for the whole facility
        measureReport.addGroup(
                getFacilityOverallTotals(location, locationData)
        );

        // Pull down Trac2esBedTypes CodeSystem from CQF
        // Loop each and add Group/Population
        CodeSystem targetCodeSystem = fhirEvalService.getCodeSystemById(this.targetBedTypesCodeSystem);

        for (CodeSystem.ConceptDefinitionComponent concept : targetCodeSystem.getConcept()) {
            MeasureReport.MeasureReportGroupComponent group = new MeasureReport.MeasureReportGroupComponent();
            CodeableConcept groupCodeableConcept = new CodeableConcept();
            Coding groupCoding = new Coding();
            // Setting system to URL of the Target Bed Type CodeSystem we pull above (and are looping now)
            groupCoding.setSystem(targetCodeSystem.getUrl());
            groupCoding.setCode(concept.getCode());
            groupCodeableConcept.addCoding(groupCoding);
            group.setCode(groupCodeableConcept);
            measureReport.addGroup(group);

            // 3 Populations

            // Total Beds for this Facility & this TRAC2ES Code EXCEPT those Bed Status in REMOVE_BED_TYPES
            MeasureReport.MeasureReportGroupPopulationComponent totalPopulationGroup = new MeasureReport.MeasureReportGroupPopulationComponent();
            totalPopulationGroup.setCount(
                    (int) locationData.stream()
                            .filter(data ->
                                    location.getName().equals(data.getFacility()) &&
                                            concept.getCode().equals(data.getTrac2es()) &&
                                            !removeBedTypes.contains(data.getBedStatus())
                            )
                            .count()
            );
            CodeableConcept totalBedsCodeableConcept = ndmsUtility.getTotPopulationCodeByTrac2es(this.evaluationServiceConfig, this.targetNdmsConceptMap, concept.getCode());
            totalPopulationGroup.setCode(totalBedsCodeableConcept);
            group.addPopulation(totalPopulationGroup);

            // Total Occupied Beds for this Facility & this TRAC2ES Code
            MeasureReport.MeasureReportGroupPopulationComponent occupiedPopulationGroup = new MeasureReport.MeasureReportGroupPopulationComponent();
            occupiedPopulationGroup.setCount(
                    (int) locationData.stream()
                            .filter(data ->
                                    location.getName().equals(data.getFacility()) &&
                                            concept.getCode().equals(data.getTrac2es()) &&
                                            occupiedBedTypes.contains(data.getBedStatus())
                            )
                            .count()
            );
            CodeableConcept occupiedBedsCodeableConcept = ndmsUtility.getOccPopulationCodeByTrac2es(this.evaluationServiceConfig, this.targetNdmsConceptMap, concept.getCode());
            occupiedPopulationGroup.setCode(occupiedBedsCodeableConcept);
            group.addPopulation(occupiedPopulationGroup);

            // Total Available Beds for this Facility & this TRAC2ES Code
            MeasureReport.MeasureReportGroupPopulationComponent availablePopulationGroup = new MeasureReport.MeasureReportGroupPopulationComponent();
            availablePopulationGroup.setCount(
                    (int) locationData.stream()
                            .filter(data ->
                                    location.getName().equals(data.getFacility()) &&
                                            concept.getCode().equals(data.getTrac2es()) &&
                                            availableBedTypes.contains(data.getBedStatus())
                            )
                            .count()
            );
            CodeableConcept availableBedsCodeableConcept = ndmsUtility.getAvailPopulationCodeByTrac2es(this.evaluationServiceConfig, this.targetNdmsConceptMap, concept.getCode());
            availablePopulationGroup.setCode(availableBedsCodeableConcept);
            group.addPopulation(availablePopulationGroup);

        }

        // Add  Organization Information to MeasureReport
        ndmsUtility.addLocationSubjectToMeasureReport(measureReport, location);

        MeasureReportSort.sortMeasureReportGroups(measureReport);

        return measureReport;
    }

    private MeasureReport.MeasureReportGroupComponent getFacilityOverallTotals(Location location, List<MethodistDataModel> locationData) {
        // Let's add the group & counts for the whole facility
        MeasureReport.MeasureReportGroupComponent overallGroup = new MeasureReport.MeasureReportGroupComponent();
        CodeableConcept overallGroupCodeableConcept = new CodeableConcept(NdmsConstants.NDMS_BEDS_CODE);
        overallGroup.setCode(overallGroupCodeableConcept);

        // 3 populations
        // numTotBeds (all for Facility except BLOCKED & OUT_OF_SERVICE
        MeasureReport.MeasureReportGroupPopulationComponent populationGroup = new MeasureReport.MeasureReportGroupPopulationComponent();
        populationGroup.setCount(
                (int) locationData.stream()
                        .filter(data ->
                                location.getName().equals(data.getFacility()) &&
                                        !removeBedTypes.contains(data.getBedStatus())
                        )
                        .count()
        );

        CodeableConcept populationGroupCodeableConcept = new CodeableConcept(NdmsConstants.NDMS_OVERALL_TOTAL_CODE);
        populationGroup.setCode(populationGroupCodeableConcept);
        overallGroup.addPopulation(populationGroup);

        // numTotBedsOcc (all for Facility where OCCUPIED)
        populationGroup = new MeasureReport.MeasureReportGroupPopulationComponent();
        populationGroup.setCount(
                (int) locationData.stream()
                        .filter(data ->
                                location.getName().equals(data.getFacility()) &&
                                        occupiedBedTypes.contains(data.getBedStatus())
                        )
                        .count()
        );
        populationGroupCodeableConcept = new CodeableConcept(NdmsConstants.NDMS_OVERALL_OCC_CODE);
        populationGroup.setCode(populationGroupCodeableConcept);
        overallGroup.addPopulation(populationGroup);

        // numTotBedsAvail (all for Facility where AVAILABLE, CLEANING, DIRTY)
        populationGroup = new MeasureReport.MeasureReportGroupPopulationComponent();
        populationGroup.setCount(
                (int) locationData.stream()
                        .filter(data ->
                                location.getName().equals(data.getFacility()) &&
                                        availableBedTypes.contains(data.getBedStatus())
                        )
                        .count()
        );
        populationGroupCodeableConcept = new CodeableConcept(NdmsConstants.NDMS_OVERALL_AVAIL_CODE);
        populationGroup.setCode(populationGroupCodeableConcept);
        overallGroup.addPopulation(populationGroup);

        return overallGroup;
    }
}
