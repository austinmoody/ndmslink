package com.lantanagroup.link.ndms;

import com.lantanagroup.link.FhirDataProvider;
import com.lantanagroup.link.Helper;
import com.lantanagroup.link.IReportAggregator;
import com.lantanagroup.link.config.api.ApiConfig;
import com.lantanagroup.link.config.api.EpicTotalsDataBundleConfig;
import com.lantanagroup.link.model.ReportContext;
import com.lantanagroup.link.model.ReportCriteria;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.MeasureReport;
import org.hl7.fhir.r4.model.Period;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

@Component
public class NdmsAggregator implements IReportAggregator {

    @Override
    public MeasureReport generate(ReportCriteria criteria, ReportContext reportContext, ReportContext.MeasureContext measureContext, ApiConfig apiConfig) throws ParseException, ExecutionException {

        MeasureReport totalsMeasure = loadTotalsMeasureReport(apiConfig, measureContext.getBundleId());
        NdmsUtility ndmsUtility = new NdmsUtility();

        // Create the master measure report
        MeasureReport masterMeasureReport = new MeasureReport();
        masterMeasureReport.setId(measureContext.getReportId());
        masterMeasureReport.setType(MeasureReport.MeasureReportType.SUMMARY);
        masterMeasureReport.setStatus(MeasureReport.MeasureReportStatus.COMPLETE);
        masterMeasureReport.setPeriod(new Period());
        masterMeasureReport.getPeriod().setStart(Helper.parseFhirDate(criteria.getPeriodStart()));
        masterMeasureReport.getPeriod().setEnd(Helper.parseFhirDate(criteria.getPeriodEnd()));
        masterMeasureReport.setMeasure(measureContext.getMeasure().getUrl());

        //TODO: Add reporter

        Map<BedTallyKey, Integer> bedTypeTally = new HashMap<>();
        // Loop all the patient-level MeasureReports and tally up count by code
        for (MeasureReport measureReport: measureContext.getPatientReports()) {
            for (MeasureReport.MeasureReportGroupComponent group: measureReport.getGroup()) {
                // Get code for the group
                // Right now there is 1 code per group for the NHSN stuff
                CodeableConcept groupCodeableConcept = group.getCode();
                Coding groupCoding = groupCodeableConcept.getCodingFirstRep();

                // Loop populations in the group
                for (MeasureReport.MeasureReportGroupPopulationComponent population: group.getPopulation()) {
                    // Get code for the population, again each population should only have 1 code
                    CodeableConcept populationCodeableConcept = population.getCode();
                    Coding populationCode = populationCodeableConcept.getCodingFirstRep();

                    // Save / Increment by group & population code.  Group Code = Bed Type.  Population Code
                    // is the count type (like occupied or total or available)
                    BedTallyKey bedTallyKey = new BedTallyKey(groupCoding, populationCode);
                    bedTypeTally.merge(bedTallyKey, population.getCount(), Integer::sum);
                }
            }
        }

        // Loop the Totals report we have pulled from storage.  This has the "totals" for the facility and for each Bed Type.
        // For each group there, add the "occupied" population from the tally above
        // For each group there, add the "available" population by calculation from the total and the occupied
        int overallOccupied = 0;
        for (MeasureReport.MeasureReportGroupComponent totalsGroup: totalsMeasure.getGroup()) {

            // REFACTOR: Remove 'Beds' hardcode
            if (totalsGroup.getCode().getCodingFirstRep().getCode().equals("Beds")) {
                // Beds = the "overall" total of beds for the facility.  We'll add
                // this section after the rest of the compilation.
                break;
            }

            // For Available Calculation
            // Get the totals.  We'll subtract occupied from it
            // REFACTOR: Assuming there is only 1 population for the Group
            int availableCount = totalsGroup.getPopulationFirstRep().getCount();
            int occupiedCount = 0;

            // Find this Group code in bedTypeTally and add that occupied population
            MeasureReport.MeasureReportGroupPopulationComponent occupied = null;
            for (Map.Entry<BedTallyKey, Integer> entry : bedTypeTally.entrySet()) {
                BedTallyKey key = entry.getKey();

                if (
                        // REFACTOR: Assuming totals group only has 1 code
                        (key.getBedType().getCode().equals(totalsGroup.getCode().getCodingFirstRep().getCode())) &&
                                (key.getBedType().getSystem().equals(totalsGroup.getCode().getCodingFirstRep().getSystem()))
                ) {
                    // We have been able to aggregate occupied beds for this TRAC2ES Bed Type
                    occupied = new MeasureReport.MeasureReportGroupPopulationComponent();
                    occupied.setCode(new CodeableConcept(key.getTallyType()));
                    occupied.setCount(entry.getValue());
                    occupiedCount = occupied.getCount();
                    totalsGroup.getPopulation().add(occupied);
                }
            }

            if (occupied == null) {
                // We didn't find any occupied beds for this "type".  So we still
                // need to add the population group for it with total = 0
                occupied = new MeasureReport.MeasureReportGroupPopulationComponent();
                // REFACTOR: Assuming totals group only has 1 code
                CodeableConcept populationOccupiedCodeableConcept = ndmsUtility.getOccPopulationCodeByTrac2es(apiConfig.getEvaluationService(), apiConfig.getTrac2esNdmsConceptMap(), totalsGroup.getCode().getCodingFirstRep().getCode());
                occupied.setCode(populationOccupiedCodeableConcept);
                occupied.setCount(occupiedCount);
                totalsGroup.getPopulation().add(occupied);
            }

            // Lookup the available population code via ConceptMap using TRAC2ES code from Total's Group Code
            availableCount -= occupiedCount;
            CodeableConcept populationAvailCodeableConcept = ndmsUtility.getAvailPopulationCodeByTrac2es(apiConfig.getEvaluationService(), apiConfig.getTrac2esNdmsConceptMap(), totalsGroup.getCode().getCodingFirstRep().getCode());
            MeasureReport.MeasureReportGroupPopulationComponent available = new MeasureReport.MeasureReportGroupPopulationComponent();
            available.setCode(populationAvailCodeableConcept);
            available.setCount(availableCount);
            totalsGroup.getPopulation().add(available);

            masterMeasureReport.addGroup(totalsGroup);

            overallOccupied += occupiedCount;
        }

        // Now we need to add the "overall" totals
        int finalOverallOccupied = overallOccupied;
        totalsMeasure.getGroup().stream().filter(
                grp -> grp.getCode().getCodingFirstRep().getCode().equals("Beds")
        ).findFirst().ifPresent(grp -> {

            // Calculate overall available
            // REFACTOR: Assuming population only has 1 group
            int overallAvailable = grp.getPopulationFirstRep().getCount() - finalOverallOccupied;

            MeasureReport.MeasureReportGroupPopulationComponent occupiedPopulation = new MeasureReport.MeasureReportGroupPopulationComponent();
            CodeableConcept occupiedCodeableConcept = new CodeableConcept(NdmsConstants.NDMS_OVERALL_OCC_CODE);
            occupiedPopulation.setCode(occupiedCodeableConcept);
            occupiedPopulation.setCount(finalOverallOccupied);
            grp.addPopulation(occupiedPopulation);

            MeasureReport.MeasureReportGroupPopulationComponent availPopulation = new MeasureReport.MeasureReportGroupPopulationComponent();
            CodeableConcept availCodeableConcept = new CodeableConcept(NdmsConstants.NDMS_OVERALL_AVAIL_CODE);
            availPopulation.setCode(availCodeableConcept);
            availPopulation.setCount(overallAvailable);
            grp.addPopulation(availPopulation);

            masterMeasureReport.addGroup(grp);
        });

        return masterMeasureReport;
    }

    private MeasureReport loadTotalsMeasureReport(ApiConfig apiConfig, String bundleId) throws ExecutionException {
        FhirDataProvider dataStore = new FhirDataProvider(apiConfig.getDataStore());

        Optional<EpicTotalsDataBundleConfig> epicTotalsData =
        apiConfig.getEpicTotalsData()
                .getBundles()
                .stream()
                .filter(
                        bundle -> bundle.getBundleId().equals(bundleId)
                )
                .findFirst();

        if (epicTotalsData.isPresent()) {
            return dataStore.getMeasureReportById(epicTotalsData.get().getTotalsReportId());
        } else {
            throw new ExecutionException(String.format("EPIC Totals Report for %s not found", bundleId), new Throwable());
        }

    }
}
