package com.lantanagroup.link;

import com.lantanagroup.link.auth.LinkCredentials;
import org.hl7.fhir.r4.model.Annotation;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Task;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class TaskHelper {
    public static Task getNewTask(LinkCredentials user, Coding taskType) {
        Task responseTask = new Task();
        responseTask.setId(UUID.randomUUID().toString());
        responseTask.setStatus(Task.TaskStatus.INPROGRESS);
        responseTask.setIntent(Task.TaskIntent.UNKNOWN);
        responseTask.setPriority(Task.TaskPriority.ROUTINE);
        responseTask.setAuthoredOn(new Date());
        responseTask.setLastModified(new Date());

        Reference callingUser = new Reference();
        callingUser.setReference(String.format("Practitioner/%s", user.getPractitioner().getIdentifier().get(0).getValue()));
        responseTask.setRequester(callingUser);

        responseTask.getMeta().addTag(taskType);

        return responseTask;
    }

    public static Annotation getAnnotationFromString(String note) {
        Annotation annotation = new Annotation();
        annotation.setText(note);
        return annotation;
    }
}
