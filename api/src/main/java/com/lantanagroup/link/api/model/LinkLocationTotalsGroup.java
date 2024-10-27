package com.lantanagroup.link.api.model;

import lombok.Getter;
import lombok.Setter;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.MeasureReport;

import java.util.List;

@Getter
@Setter
public class LinkLocationTotalsGroup {
    private String code;
    private String system;
    private List<LinkLocationTotalsPopulation> populations;

    public MeasureReport.MeasureReportGroupComponent toMeasureReportGroupComponent() {
        MeasureReport.MeasureReportGroupComponent group = new MeasureReport.MeasureReportGroupComponent();

        CodeableConcept codeableConcept = new CodeableConcept();
        Coding coding = new Coding();
        coding.setCode(code);
        coding.setSystem(system);
        codeableConcept.addCoding(coding);
        group.setCode(codeableConcept);

        for (LinkLocationTotalsPopulation population : this.populations) {
            group.addPopulation(population.toMeasureReportGroupPopulationComponent());
        }

        return group;
    }
}
