package com.lantanagroup.link;

import com.lantanagroup.link.auth.LinkCredentials;
import com.lantanagroup.link.helpers.RemoteAddressHelper;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Task;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Annotation;

import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.UUID;

public class TaskHelper {

    private TaskHelper() {
        throw new IllegalStateException("Helper class");
    }

    public static Task getNewTask(LinkCredentials user, HttpServletRequest httpServletRequest, Coding taskType) {
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

        // Add remote address
        String remoteAddress = RemoteAddressHelper.getRemoteAddressFromRequest(httpServletRequest);
        Task.ParameterComponent remoteAddressInput = new Task.ParameterComponent();
        remoteAddressInput.setType(new CodeableConcept().addCoding(
                Constants.REMOTE_ADDRESS
        ));
        remoteAddressInput.setValue(new StringType(remoteAddress));
        responseTask.addInput(remoteAddressInput);

        responseTask.getMeta().addTag(taskType);

        return responseTask;
    }

    public static Annotation getAnnotationFromString(String note) {
        Annotation annotation = new Annotation();
        annotation.setText(note);
        return annotation;
    }
}
