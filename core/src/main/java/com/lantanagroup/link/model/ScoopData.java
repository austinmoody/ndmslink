package com.lantanagroup.link.model;

import lombok.Getter;
import lombok.Setter;
import org.hl7.fhir.r4.model.Annotation;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import java.util.Date;

@Getter
@Setter
public class ScoopData {
    @NotBlank(message = "Period Start is required")
    private String periodStart;
    @NotBlank(message = "Period End is required")
    private String periodEnd;
    @NotEmpty(message = "At least one Bundle ID is required")
    private String[] bundleIds;
    @NotBlank(message = "Organization is required")
    private String organizationId;

    public Annotation getAnnotation() {
        Annotation annotation = new Annotation();
        annotation.setTime(new Date());
        annotation.setText(String.format("ScoopData parameters: Organization - %s / periodStart - %s / periodEnd - %s / bundleIds - %s",
                organizationId,
                periodStart,
                periodEnd,
                String.join(",", bundleIds)));
        return annotation;
    }
}
