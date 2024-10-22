package com.lantanagroup.link.model;

import lombok.Getter;
import lombok.Setter;
import org.hl7.fhir.r4.model.Annotation;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

@Getter
@Setter
public class GenerateReport {
    @NotNull(message = "Regenerate flag must be specified")
    boolean regenerate;
    @NotBlank(message = "Report Period Start must be specified")
    private String periodStart;
    @NotBlank(message = "Report Period End must be specified")
    private String periodEnd;
    // Used to lookup "Generate Report Configuration" from ApiConfig
    @NotBlank(message = "Report Organization must be specified")
    private String organizationId;

    public Annotation getAnnotation() {
        Annotation annotation = new Annotation();
        annotation.setTime(new Date());
        annotation.setText(
                String.format("Generate Report parameters: Organization: %s / Period Start: %s / Period End: %s / Regenerate?: %s",
                organizationId,
                getPeriodStart(),
                getPeriodEnd(),
                regenerate)
        );
        return annotation;
    }

    // Simulating old ReportIdHelper
    public String getMasterIdentifierValue() {
        Collection<String> components = new LinkedList<>();
        components.add(organizationId);
        components.add(periodStart);
        components.add(periodEnd);

        return Integer.toHexString(
                String.join("-", components).hashCode()
        );
    }

}
