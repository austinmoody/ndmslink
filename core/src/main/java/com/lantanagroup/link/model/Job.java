package com.lantanagroup.link.model;

import com.lantanagroup.link.Constants;
import lombok.Getter;
import lombok.Setter;
import org.hl7.fhir.r4.model.Annotation;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Task;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Getter
@Setter
public class Job {
    private String id;
    private String status;
    private String type;
    private Date created;
    private Date lastUpdated;
    private List<JobNote> notes;

    public Job() {}

    public Job(Task task) {
        id = task.getIdElement().getIdPart();
        // Getting the "code" of status element gives us completed, in-progress, etc...
        // to make the return of this via API JSON look like a FHIR Task.
        status = task.getStatusElement().getCode();
        created = task.getAuthoredOn();
        lastUpdated = task.getLastModified();

        // Label "type" of job
        for (Coding tag : task.getMeta().getTag()) {
            if (tag.getSystem().equals(Constants.SANER_JOB_TYPE_SYSTEM)) {
                type = tag.getCode();
            }
        }

        notes = new ArrayList<>();

        for (Annotation taskNote : task.getNote()) {
            notes.add(new JobNote(taskNote));
        }
    }
}
