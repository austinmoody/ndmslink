package com.lantanagroup.link.model;

import lombok.Getter;
import lombok.Setter;
import org.hl7.fhir.r4.model.Annotation;

import java.util.Date;

@Getter
@Setter
public class JobNote {
    private Date date;
    private String note;

    public JobNote() {}

    public JobNote(Annotation taskNote) {
        date = taskNote.getTime();
        note = taskNote.getText();
    }
}
