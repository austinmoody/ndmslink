package com.lantanagroup.link.model;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;

@Getter
@Setter
public class ScoopData {
    @NotBlank(message = "Period Start is required")
    private String periodStart;
    @NotBlank(message = "Period End is required")
    private String periodEnd;
    @NotEmpty(message = "At least one Bundle ID is required")
    private String[] bundleIds;
}
