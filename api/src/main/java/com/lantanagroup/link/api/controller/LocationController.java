package com.lantanagroup.link.api.controller;

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import ca.uhn.fhir.rest.server.exceptions.ResourceGoneException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import com.lantanagroup.link.*;
import com.lantanagroup.link.Constants;
import com.lantanagroup.link.api.ApiUtility;
import com.lantanagroup.link.api.model.LinkLocation;
import com.lantanagroup.link.api.model.LinkLocationTotals;
import com.lantanagroup.link.api.model.LinkLocationTotalsGroup;
import com.lantanagroup.link.api.model.LinkLocationTotalsPopulation;
import com.lantanagroup.link.auth.LinkCredentials;
import com.lantanagroup.link.model.Job;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PreDestroy;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.Date;
import java.util.List;
import java.util.Optional;
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

    @RequestMapping(value={"/location/totals"}, method = {RequestMethod.PUT, RequestMethod.POST})
    public ResponseEntity<Object> createUpdateTotals(
            @AuthenticationPrincipal LinkCredentials user,
            HttpServletRequest request,
            @Valid @RequestBody LinkLocationTotals locationTotals
    ) {

        Task task = TaskHelper.getNewTask(user, request, Constants.TOTALS_CREATE_UPDATE);
        FhirDataProvider fhirDataProvider = getFhirDataProvider();

        try {

            MeasureReport totalsReport = getExistingMeasureReport(locationTotals.getId());

            if (totalsReport == null) {
                totalsReport = createBaseMeasureReport(locationTotals.getId(), locationTotals.getMeasure());
                ApiUtility.addNoteToTask(task,
                        String.format("Totals Report with id '%s' not found, creating", locationTotals.getId()));

            }

            updateReport(totalsReport, locationTotals.getGroups());

            fhirDataProvider.updateResource(totalsReport);

            this.getFhirDataProvider().audit(task,
                    user.getJwt(),
                    FhirHelper.AuditEventTypes.TOTALS_CREATE_UPDATE,
                    "Successfully Created/Update Totals");

            task.setStatus(Task.TaskStatus.COMPLETED);
            ApiUtility.addNoteToTask(task, "Totals Created/Updated Successfully");

            fhirDataProvider.updateResource(task);
        } catch (Exception ex) {
            String errorMessage = String.format("Issue with create/update Totals API call: %s", ex.getMessage());
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

    private void updateGroup(MeasureReport report, LinkLocationTotalsGroup totalsGroup) {
        report.getGroup().stream()
                .filter(group -> matchesGroupCoding(group, totalsGroup))
                .forEach(group -> updatePopulations(group, totalsGroup.getPopulations()));
    }

    private boolean matchesGroupCoding(MeasureReport.MeasureReportGroupComponent group, LinkLocationTotalsGroup totalsGroup) {
        return group.getCode().getCoding().stream()
                .anyMatch(coding -> coding.getSystem().equals(totalsGroup.getSystem())
                        && coding.getCode().equals(totalsGroup.getCode()));
    }

    private void updatePopulations(MeasureReport.MeasureReportGroupComponent group,
                                   List<LinkLocationTotalsPopulation> totalsPopulations) {
        for (LinkLocationTotalsPopulation totalsPopulation : totalsPopulations) {
            Optional<MeasureReport.MeasureReportGroupPopulationComponent> existingPopulation =
                    findMatchingPopulation(group, totalsPopulation);

            if (existingPopulation.isPresent()) {
                existingPopulation.get().setCount(totalsPopulation.getCount());
            } else {
                group.addPopulation(totalsPopulation.toMeasureReportGroupPopulationComponent());
            }
        }
    }

    private Optional<MeasureReport.MeasureReportGroupPopulationComponent> findMatchingPopulation(
            MeasureReport.MeasureReportGroupComponent group,
            LinkLocationTotalsPopulation totalsPopulation) {
        return group.getPopulation().stream()
                .filter(pop -> matchesPopulationCoding(pop, totalsPopulation))
                .findFirst();
    }

    private boolean matchesPopulationCoding(
            MeasureReport.MeasureReportGroupPopulationComponent population,
            LinkLocationTotalsPopulation totalsPopulation) {
        return population.getCode().getCoding().stream()
                .anyMatch(coding -> coding.getSystem().equals(totalsPopulation.getSystem())
                        && coding.getCode().equals(totalsPopulation.getCode()));
    }

    private void updateReport(MeasureReport report, List<LinkLocationTotalsGroup> totalsGroups) {

        for (LinkLocationTotalsGroup totalsGroup : totalsGroups) {

            if (!groupExists(report, totalsGroup)) {
                report.addGroup(totalsGroup.toMeasureReportGroupComponent());
                // Go ahead and return here because if the group didn't exist then we have
                // added the Group and corresponding Population so there is no need to go
                // forwarded and update the Population.
            } else {
                // Group exists, so update population(s) as necessary
                updateGroup(report, totalsGroup);
            }
        }
    }

    private MeasureReport getExistingMeasureReport(String measureReportId) {
        FhirDataProvider fhirDataProvider = getFhirDataProvider();
        try {
            return fhirDataProvider.getMeasureReportById(measureReportId);
        } catch (ResourceGoneException | ResourceNotFoundException ex) {
            // If there was a copy of the MeasureReport out there with the same ID previously, which was
            // deleted but not "expunged" you will get an HTTP 410 GONE code back from the FHIR Server.
            // We don't care about that because if everything else goes well we will re-create and store
            // the report.
            // Also, if there is no existing report we'll get a 404 NOT FOUND.  Also don't care about that.
            return null;
        }
    }

    private MeasureReport createBaseMeasureReport(String totalsReportId, String measure) {
        Date date = new Date();

        MeasureReport measureReport = new MeasureReport();
        measureReport.setId(totalsReportId);
        measureReport.getMeta().addProfile(Constants.MEASURE_REPORT_PROFILE);
        measureReport.setStatus(MeasureReport.MeasureReportStatus.COMPLETE);
        measureReport.setType(MeasureReport.MeasureReportType.SUMMARY);
        measureReport.setMeasure(measure);
        measureReport.setDate(date);
        measureReport.getPeriod()
                .setStart(date, TemporalPrecisionEnum.DAY)
                .setEnd(date, TemporalPrecisionEnum.DAY);
        return measureReport;
    }

    private boolean groupExists(MeasureReport measureReport, LinkLocationTotalsGroup totalsGroup) {
        boolean groupExists = false;
        for (MeasureReport.MeasureReportGroupComponent group : measureReport.getGroup()) {
            CodeableConcept groupCodeableConcept = group.getCode();
            for (Coding groupCoding : groupCodeableConcept.getCoding()) {
                if (
                        groupCoding.getSystem().equals(totalsGroup.getSystem())
                                && groupCoding.getCode().equals(totalsGroup.getCode())
                ) {
                    groupExists = true;
                }
            }
        }

        return groupExists;
    }

}
