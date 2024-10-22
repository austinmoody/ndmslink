package com.lantanagroup.link.model;

import lombok.Getter;
import lombok.Setter;
import org.hl7.fhir.r4.model.Annotation;

import javax.validation.constraints.NotBlank;
import java.util.Date;

@Getter
@Setter
public class ScoopData {
    @NotBlank(message = "Period Start is required")
    private String periodStart;
    @NotBlank(message = "Period End is required")
    private String periodEnd;
    @NotBlank(message = "Location is required")
    private String locationId;
    @NotBlank(message = "Measure is required")
    private String measureId;

    public Annotation getAnnotation() {
        Annotation annotation = new Annotation();
        annotation.setTime(new Date());
        annotation.setText(String.format("ScoopData parameters: Location - %s / Measure - %s / periodStart - %s / periodEnd - %s",
                locationId,
                measureId,
                periodStart,
                periodEnd
                )
        );
        return annotation;
    }
}
