package com.lantanagroup.link.cli.models;

import lombok.Getter;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.MeasureReport;

@Getter
public class EpicTotalDataSet {
    private final String groupCode;
    private final String groupSystem;
    private final String populationCode;
    private final String populationSystem;
    private final int populationCount;

    public EpicTotalDataSet(String groupCode, String groupSystem, String populationCode, String populationSystem, int populationCount) {
        this.groupCode = groupCode;
        this.groupSystem = groupSystem;
        this.populationCode = populationCode;
        this.populationSystem = populationSystem;
        this.populationCount = populationCount;
    }

    public MeasureReport.MeasureReportGroupComponent toMeasureReportGroupComponent() {
        MeasureReport.MeasureReportGroupComponent group = new MeasureReport.MeasureReportGroupComponent();

        CodeableConcept codeableConcept = new CodeableConcept();
        Coding coding = new Coding();
        coding.setCode(groupCode);
        coding.setSystem(groupSystem);
        codeableConcept.addCoding(coding);
        group.setCode(codeableConcept);

        group.addPopulation(toMeasureReportGroupPopulationComponent());

        return group;
    }

    public MeasureReport.MeasureReportGroupPopulationComponent toMeasureReportGroupPopulationComponent() {
        MeasureReport.MeasureReportGroupPopulationComponent population = new MeasureReport.MeasureReportGroupPopulationComponent();
        CodeableConcept codeableConcept = new CodeableConcept();
        Coding coding = new Coding();
        coding.setCode(populationCode);
        coding.setSystem(populationSystem);
        codeableConcept.addCoding(coding);
        population.setCode(codeableConcept);
        population.setCount(populationCount);
        return population;
    }

    @Override
    public String toString() {
        return String.format("GroupCode: %s, GroupSystem: %s, PopulationCode: %s, PopulationSystem: %s, PopulationCount: %d",
                groupCode, groupSystem, populationCode, populationSystem, populationCount);
    }
}
