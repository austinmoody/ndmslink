package com.lantanagroup.link.api.controller;

import com.lantanagroup.link.Constants;
import com.lantanagroup.link.FhirDataProvider;
import com.lantanagroup.link.FhirHelper;
import com.lantanagroup.link.TaskHelper;
import com.lantanagroup.link.auth.LinkCredentials;
import com.lantanagroup.link.config.api.CsvProcessor;
import com.lantanagroup.link.csv.ICsvProcessor;
import com.lantanagroup.link.model.Job;
import com.lantanagroup.link.model.ReportCriteria;
import org.hl7.fhir.r4.model.Annotation;
import org.hl7.fhir.r4.model.MeasureReport;
import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PreDestroy;
import javax.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/csv")
public class CsvProcessorController extends BaseController {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Logger logger = LoggerFactory.getLogger(CsvProcessorController.class);
    private final ApplicationContext applicationContext;

    public CsvProcessorController(ApplicationContext applicationContext) {
        super();
        this.applicationContext = applicationContext;
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }

    @PostMapping(value="/create-measure-reports/{locationId}", consumes = "text/csv")
    public ResponseEntity<Object> createMeasureReports(
            @AuthenticationPrincipal LinkCredentials user,
            @RequestBody() String csvContent,
            HttpServletRequest request,
            @PathVariable String locationId
            ) {

        Task task = TaskHelper.getNewTask(user, request, Constants.CSV_TO_MEASURE_REPORT);
        FhirDataProvider fhirDataProvider = getFhirDataProvider();

        try {
            fhirDataProvider.updateResource(task);
            executor.submit(() -> createMeasureReports(user, task.getId(), locationId, csvContent));

        } catch (Exception ex) {
            String errorMessage = String.format("Issue with CSV to MeasureReport conversion: %s", ex.getMessage());
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

    private void createMeasureReports(LinkCredentials user, String taskId, String locationId, String csvContent) {

        // Get the task so that it can be updated later
        FhirDataProvider fhirDataProvider = getFhirDataProvider();
        Task task = fhirDataProvider.getTaskById(taskId);

        try {
            Optional<CsvProcessor> csvProcessor = config.getCsvProcessors().stream().filter(
                    csvProc -> csvProc.getLocationId().equals(locationId)
            ).findFirst();

            if (csvProcessor.isEmpty()) {
                throw new IllegalStateException("No CSV Processor found for locationId: " + locationId);
            }

            // By default, we'll create the ReportCriteria w/ today's date
            // The source CSV in some cases could perhaps specify dates, it will be
            // the responsibility of the individual CSV Processor to handle updating
            // the criteria.
            ReportCriteria reportCriteria = new ReportCriteria(
                    new ArrayList<>(),
                    locationId,
                    csvProcessor.get().getMeasure(),
                    getTodayPeriodStart(),
                    getTodayPeriodEnd()
            );

            String csvProcessorClassName = csvProcessor.get().getCsvProcessorClass();
            Class<?> csvProcessorClass = Class.forName(csvProcessorClassName);
            ICsvProcessor csvProcessorInstance = (ICsvProcessor) applicationContext.getBean(csvProcessorClass);

            // Data Store - needed to pull Location(s), possibly other things
            // Evaluation (CQF) - needed to pull ConceptMap(s) and CodeSystem(s)
            List<MeasureReport> measureReports = csvProcessorInstance.process(config.getDataStore(), config.getEvaluationService(), reportCriteria, csvProcessor.get(), csvContent);

            task.addNote(
                    new Annotation()
                            .setText(
                                    String.format("CSV to MeasureReport Conversion Complete with %d reports  created", measureReports.size())
                            )
                            .setTime(new Date())
            );

            // Loop & Store the MeasureReport(s)
            for (MeasureReport measureReport : measureReports) {
                fhirDataProvider.updateResource(measureReport);
            }

            task.setStatus(Task.TaskStatus.COMPLETED);

            this.getFhirDataProvider().audit(task,
                    user.getJwt(),
                    FhirHelper.AuditEventTypes.CSV_TO_MEASUREREPORT_CONVERSION,
                    "Successfully Initiated CSV to MeasureReport Conversion");

        } catch (Exception ex) {
            String errorMessage = String.format("Issue with creating MeasureReport(s) from CSV: %s", ex.getMessage());
            logger.error(errorMessage);
            Annotation note = new Annotation();
            note.setText(errorMessage);
            note.setTime(new Date());
            task.addNote(note);
            task.setStatus(Task.TaskStatus.FAILED);
        } finally {
            task.setLastModified(new Date());
            fhirDataProvider.updateResource(task);
        }

    }

    private String getTodayPeriodStart() {
        return LocalDate.now()
                .atStartOfDay()
                .atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_INSTANT);
    }

    private String getTodayPeriodEnd() {
        return LocalDate.now()
                .atTime(23, 59, 59, 999_000_000)
                .atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_INSTANT);
    }

}
