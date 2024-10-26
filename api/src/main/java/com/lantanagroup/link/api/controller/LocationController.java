package com.lantanagroup.link.api.controller;

import com.lantanagroup.link.Constants;
import com.lantanagroup.link.FhirDataProvider;
import com.lantanagroup.link.FhirHelper;
import com.lantanagroup.link.TaskHelper;
import com.lantanagroup.link.api.ApiUtility;
import com.lantanagroup.link.api.model.LinkLocation;
import com.lantanagroup.link.auth.LinkCredentials;
import com.lantanagroup.link.model.Job;
import org.hl7.fhir.r4.model.Annotation;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PreDestroy;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api")
public class LocationController extends BaseController {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Logger logger = LoggerFactory.getLogger(LocationController.class);

    public LocationController() {
        super();
    }

    @PreDestroy
    public void shutdown() {
        // needed to avoid resource leak
        executor.shutdown();
    }

    @RequestMapping(value={"/location"}, method = {RequestMethod.PUT, RequestMethod.POST})
    public ResponseEntity<Object> createLocation(
            @AuthenticationPrincipal LinkCredentials user,
            HttpServletRequest request,
            @Valid @RequestBody LinkLocation location
    ) {

        // POST = Create
        // PUT = Update
        // We'll answer to either but ultimately calling "update" on FHIR Data Provider
        Coding taskType = Constants.LOCATION_CREATE_UPDATE;
        FhirHelper.AuditEventTypes auditEvent = FhirHelper.AuditEventTypes.LOCATION_CREATE_UPDATE;

        Task task = TaskHelper.getNewTask(user, request, taskType);
        FhirDataProvider fhirDataProvider = getFhirDataProvider();

        try {
            fhirDataProvider.updateResource(
                    location.toFhirLocation()
            );

            this.getFhirDataProvider().audit(task,
                    user.getJwt(),
                    auditEvent,
                    "Successfully Create/Update Location");

            task.setStatus(Task.TaskStatus.COMPLETED);
            ApiUtility.addNoteToTask(task, "Location Created/Updated Successfully");

            fhirDataProvider.updateResource(task);
        } catch (Exception ex) {
            String errorMessage = String.format("Issue with create/update Location API call: %s", ex.getMessage());
            logger.error(errorMessage);
            task.addNote(
                    new Annotation()
                            .setText(errorMessage)
                            .setTime(new Date())
            );
            task.setStatus(Task.TaskStatus.FAILED);
            return ResponseEntity.badRequest().body(new Job(task));
        } finally {
            task.setLastModified(new Date());
            fhirDataProvider.updateResource(task);
        }
        return ResponseEntity.ok(new Job(task));
    }

}
