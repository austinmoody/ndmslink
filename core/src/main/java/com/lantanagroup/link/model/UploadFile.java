package com.lantanagroup.link.model;

import lombok.Getter;
import lombok.Setter;
import org.hl7.fhir.r4.model.Coding;

import javax.validation.constraints.NotBlank;
import java.util.Map;


@Getter
@Setter
public class UploadFile {
    private String source;

    @NotBlank(message = "Upload File type cannot be blank")
    private String type;

    @NotBlank(message = "Upload File name cannot be blank")
    private String name;

    @NotBlank(message = "Upload File content cannot be blank")
    private String content;

    private Map<String, Object> options;
}
