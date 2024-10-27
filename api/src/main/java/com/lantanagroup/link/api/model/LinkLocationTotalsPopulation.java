package com.lantanagroup.link.api.model;

import lombok.Getter;
import lombok.Setter;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.MeasureReport;

@Getter
@Setter
public class LinkLocationTotalsPopulation {
    private String code;
    private String system;
    private int count;

    public MeasureReport.MeasureReportGroupPopulationComponent toMeasureReportGroupPopulationComponent() {
        MeasureReport.MeasureReportGroupPopulationComponent population = new MeasureReport.MeasureReportGroupPopulationComponent();
        CodeableConcept codeableConcept = new CodeableConcept();
        Coding coding = new Coding();
        coding.setCode(code);
        coding.setSystem(system);
        codeableConcept.addCoding(coding);
        population.setCode(codeableConcept);
        population.setCount(count);
        return population;
    }
}
