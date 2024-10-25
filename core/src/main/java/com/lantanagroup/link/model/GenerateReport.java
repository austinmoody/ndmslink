package com.lantanagroup.link.model;

import lombok.Getter;
import lombok.Setter;
import org.hl7.fhir.r4.model.Annotation;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Date;

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
    @NotBlank(message = "Report Location must be specified")
    private String locationId;
    @NotBlank(message = "Report Measure must be specified")
    private String measureId;

    private ReportContext reportContext;
    private String taskId;

    public Annotation getAnnotation() {
        Annotation annotation = new Annotation();
        annotation.setTime(new Date());
        annotation.setText(
                String.format("Generate Report parameters: Location: %s / Measure: %s / Period Start: %s / Period End: %s / Regenerate?: %s",
                locationId,
                measureId,
                periodStart,
                periodEnd,
                regenerate)
        );
        return annotation;
    }

}
