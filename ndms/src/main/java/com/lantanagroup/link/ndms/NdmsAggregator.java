package com.lantanagroup.link.ndms;

import com.lantanagroup.link.Helper;
import com.lantanagroup.link.IReportAggregator;
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

@Component
public class NdmsAggregator implements IReportAggregator {
    @Override
    public MeasureReport generate(ReportCriteria criteria, ReportContext reportContext, ReportContext.MeasureContext measureContext) throws ParseException {
        // Create the master measure report
        MeasureReport masterMeasureReport = new MeasureReport();
        masterMeasureReport.setId(measureContext.getReportId());
        masterMeasureReport.setType(MeasureReport.MeasureReportType.SUBJECTLIST);
        masterMeasureReport.setStatus(MeasureReport.MeasureReportStatus.COMPLETE);
        masterMeasureReport.setPeriod(new Period());
        masterMeasureReport.getPeriod().setStart(Helper.parseFhirDate(criteria.getPeriodStart()));
        masterMeasureReport.getPeriod().setEnd(Helper.parseFhirDate(criteria.getPeriodEnd()));
        masterMeasureReport.setMeasure(measureContext.getMeasure().getUrl());

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

        // Loop the Bed Tally structure
        // Create a Group in the master MeasureReport for each "type" of bed.
        // Add a population to the group for each tally type (occupied, etc...)
        bedTypeTally.forEach((bedTallyKey, bedTallyTotal) -> {

            MeasureReport.MeasureReportGroupComponent group = new MeasureReport.MeasureReportGroupComponent();
            group.setCode(new CodeableConcept(bedTallyKey.getBedType()));

            MeasureReport.MeasureReportGroupPopulationComponent occupied = new MeasureReport.MeasureReportGroupPopulationComponent();
            occupied.setCode(new CodeableConcept(bedTallyKey.getTallyType()));
            occupied.setCount(bedTallyTotal);

            group.addPopulation(occupied);

            masterMeasureReport.addGroup(group);
        });


        return masterMeasureReport;
    }
}
