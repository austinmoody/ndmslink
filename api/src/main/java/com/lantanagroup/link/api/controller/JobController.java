package com.lantanagroup.link.api.controller;

import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import com.lantanagroup.link.Constants;
import com.lantanagroup.link.Helper;
import com.lantanagroup.link.model.Job;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/api/job")
public class JobController extends BaseController {
    private static final Logger logger = LoggerFactory.getLogger(JobController.class);

    @GetMapping(produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<?> searchJobs(
            @RequestParam(required = false) String id,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String createdDate,
            @RequestParam(required = false) String lastModifiedDate

    ) {
        try {
            String url = this.config.getDataStore().getBaseUrl();
            if (!url.endsWith("/")) url += "/";
            url += "Task?";

            boolean andCond = false;

            if (id != null) {
                url += String.format("_id=%s", Helper.encodeForUrl(id));
                andCond = true;
            }

            if (status != null) {
                url += String.format("%sstatus=%s",(andCond ? "&" : ""), Helper.encodeForUrl(status));
                andCond = true;
            }

            if (type != null) {
                String typeUrl = String.format("%s_tag=%s%s%s",
                        (andCond ? "&" : ""),
                        Helper.encodeForUrl(Constants.SANER_JOB_TYPE_SYSTEM),
                        "%7C",
                        Helper.encodeForUrl(type));
                url += typeUrl;
                andCond = true;
            }

            if (createdDate != null) {
                Date createdDateAsDate = Helper.parseDate(createdDate);
                Date startOfDay = Helper.getStartOfDay(createdDateAsDate);
                Date endOfDay = Helper.getEndOfDay(createdDateAsDate);
                String startOfDayString = Helper.getFhirDate(startOfDay);
                String endOfDayString = Helper.getFhirDate(endOfDay);
                url += String.format("%sauthored-on=ge%s&authored-on=le%s",
                        (andCond ? "&" : ""),
                        startOfDayString,
                        endOfDayString
                );

                andCond = true;
            }

            if (lastModifiedDate != null) {
                Date lastModDateAsDate = Helper.parseDate(lastModifiedDate);
                Date startOfDay = Helper.getStartOfDay(lastModDateAsDate);
                Date endOfDay = Helper.getEndOfDay(lastModDateAsDate);
                String startOfDayString = Helper.getFhirDate(startOfDay);
                String endOfDayString = Helper.getFhirDate(endOfDay);
                url += String.format("%s_lastUpdated=ge%s&_lastUpdated=le%s",
                        (andCond ? "&" : ""),
                        startOfDayString,
                        endOfDayString
                );
            }

            List<Job> jobs = new ArrayList<>();
            Bundle bundle = getFhirDataProvider().fetchResourceFromUrl(url);

            for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
                Task task = (Task)entry.getResource();
                jobs.add(new Job(task));
            }

            return ResponseEntity.ok(jobs);

        } catch (Exception ex) {
            String errorMessage = String.format("Searching jobs failed: '%s'", ex.getMessage());
            logger.error(errorMessage);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorMessage);
        }

    }

    @GetMapping(value = "/{jobId}")
    public ResponseEntity<?> getJob(@PathVariable("jobId") String jobId) {
        try {
            Task task = getFhirDataProvider().getTaskById(jobId);

            Job job = new Job(task);

            return ResponseEntity.ok(job);
        } catch (ResourceNotFoundException ex) {
            String errorMessage = String.format("Job with id '%s' was not found on the Data Store", jobId);
            logger.error(errorMessage);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorMessage);
        } catch (Exception ex) {
            String errorMessage = String.format("Get Job with id '%s' failed: '%s'", jobId, ex.getMessage());
            logger.error(errorMessage);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorMessage);
        }


    }
}
