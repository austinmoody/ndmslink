package com.lantanagroup.link.api.controller;

import com.lantanagroup.link.Constants;
import com.lantanagroup.link.*;
import com.lantanagroup.link.api.ApiUtility;
import com.lantanagroup.link.auth.LinkCredentials;
import com.lantanagroup.link.config.query.QueryConfig;
import com.lantanagroup.link.config.query.USCoreConfig;
import com.lantanagroup.link.model.*;
import com.lantanagroup.link.query.IQuery;
import com.lantanagroup.link.query.QueryFactory;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PreDestroy;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/report")
public class ReportController extends BaseController {
  private static final Logger logger = LoggerFactory.getLogger(ReportController.class);
  private static final String PERIOD_START_PARAM_NAME = "periodStart";
  private static final String PERIOD_END_PARAM_NAME = "periodEnd";

  // Disallow binding of sensitive attributes
  final String[] disallowedFields = new String[]{};

  private final USCoreConfig usCoreConfig;
  private final EventService eventService;
  private final ApplicationContext context;
  private final StopwatchManager stopwatchManager;

  private final ExecutorService executor = Executors.newSingleThreadExecutor();

  public ReportController(
          ApplicationContext context,
          EventService eventService,
          USCoreConfig usCoreConfig,
          StopwatchManager stopwatchManager
  ) {
    super();
    this.context = context;
    this.eventService = eventService;
    this.usCoreConfig = usCoreConfig;
    this.stopwatchManager = stopwatchManager;
  }

  @PreDestroy
  public void shutdown() {
    // needed to avoid resource leak
    executor.shutdown();
  }

  @InitBinder
  public void initBinder(WebDataBinder binder) {
    binder.setDisallowedFields(disallowedFields);
  }

  private void getPatientIdentifiers(ReportCriteria criteria, ReportContext reportContext) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
    IPatientOfInterest provider;
    Class<?> patientIdResolverClass = Class.forName(this.config.getPatientIdResolver());
    Constructor<?> patientIdentifierConstructor = patientIdResolverClass.getConstructor();
    provider = (IPatientOfInterest) patientIdentifierConstructor.newInstance();
    provider.getPatientsOfInterest(criteria, reportContext, this.config);
  }

  private void queryAndStorePatientData(List<String> resourceTypes, ReportCriteria criteria, ReportContext reportContext) throws Exception {
    List<PatientOfInterestModel> patientsOfInterest = reportContext.getPatientsOfInterest();
    String measureId = reportContext.getMeasureContext().getMeasure().getIdentifierFirstRep().getValue();
    try {
      // Get the data
      String patientsOfInterestJoined = StringUtils.join(patientsOfInterest, ", ");
      logger.info(
              "Querying/scooping data for the patients: {}", patientsOfInterestJoined
      );
      QueryConfig queryConfig = this.context.getBean(QueryConfig.class);
      IQuery query = QueryFactory.getQueryInstance(this.context, queryConfig.getQueryClass());
      query.execute(criteria, reportContext, patientsOfInterest, reportContext.getMasterIdentifier(), resourceTypes, measureId);
    } catch (Exception ex) {
      logger.error(String.format("Error scooping/storing data for the patients (%s)", StringUtils.join(patientsOfInterest, ", ")));
      throw ex;
    }
  }

  @PostMapping("/generate")
  public ResponseEntity<Object> generateReport(@AuthenticationPrincipal LinkCredentials user,
                                               HttpServletRequest request,
                                               @Valid @RequestBody GenerateReport generateReport) {

    // TODO - redo this regenerate thing.  It was all tied to DocumentReference and to be honest I don't care about
    // that anymore.  So we want ot search for the MeasureReport aggregate that would get created first.
    // Thi sis the error to throw:
    // throw new ResponseStatusException(HttpStatus.CONFLICT, "A report has already been generated for the specified measure and reporting period. To regenerate the report, submit your request with regenerate=true.");

    ReportContext reportContext = new ReportContext(this.getFhirDataProvider());
    reportContext.setRequest(request);
    reportContext.setUser(user);
    reportContext.setMasterIdentifier(
            ReportIdHelper.getMasterIdentifierValue(generateReport)
    );
    generateReport.setReportContext(reportContext);

    Task task = TaskHelper.getNewTask(user, request, Constants.GENERATE_REPORT);
    FhirDataProvider fhirDataProvider = getFhirDataProvider();
    fhirDataProvider.updateResource(task);
    Job job = new Job(task);

    generateReport.setTaskId(task.getId());

    executor.submit(
            () -> generateReport(generateReport)
    );

    return ResponseEntity.ok(job);
  }

  private void generateReport(GenerateReport generateReport) {

    // Get the task so that it can be updated later
    FhirDataProvider dataProvider = getFhirDataProvider();
    Task task = dataProvider.getTaskById(generateReport.getTaskId());

    try {

      // Add parameters used to generate report to Task
      task.addNote(generateReport.getAnnotation());

      ReportCriteria criteria = new ReportCriteria(generateReport);

      this.eventService.triggerEvent(EventTypes.BeforeMeasureResolution, criteria, generateReport.getReportContext());

      // Get/Verify Location from Data Store
      generateReport.getReportContext().setReportLocation(
              ApiUtility.getAndVerifyLocation(generateReport.getLocationId(), config.getDataStore())
      );

      // Get Measure definition, add to Report Context
      // TODO: Add this to generate-report-configuration: and key off location specified when calling /generate
      generateReport.getReportContext().setMeasureContext(
              ApiUtility.getAndVerifyMeasure(generateReport.getMeasureId(), config.getEvaluationService())
      );

      this.eventService.triggerEvent(EventTypes.AfterMeasureResolution, criteria, generateReport.getReportContext());

      // Add note to Task
      task.addNote(
              new Annotation()
                      .setText(String.format("Generating report with identifier: %s", generateReport.getReportContext().getMasterIdentifier()))
                      .setTime(new Date())
      );

      this.eventService.triggerEvent(EventTypes.BeforePatientOfInterestLookup, criteria, generateReport.getReportContext());

      // Get the patient identifiers for the given date
      getPatientIdentifiers(criteria, generateReport.getReportContext());

      // Add Lists(s) being process for report to Task
      List<String> listsIds = new ArrayList<>();
      for (ListResource lr : generateReport.getReportContext().getPatientCensusLists()) {
        listsIds.add(lr.getIdElement().getIdPart());
      }
      task.addNote(
              new Annotation()
                      .setTime(new Date())
                      .setText(String.format("Patient Census Lists processed: %s", String.join(",", listsIds)))
      );

      this.eventService.triggerEvent(EventTypes.AfterPatientOfInterestLookup, criteria, generateReport.getReportContext());

      this.eventService.triggerEvent(EventTypes.BeforePatientDataQuery, criteria, generateReport.getReportContext());

      // Get the resource types to query
      Set<String> resourceTypesToQuery = new HashSet<>(
              FhirHelper.getDataRequirementTypes(
                      generateReport.getReportContext().getMeasureContext().getReportDefBundle()
              )
      );
      resourceTypesToQuery.retainAll(usCoreConfig.getPatientResourceTypes());

      // Add list of Resource types that we are going to query to the Task
      task.addNote(
              new Annotation()
                      .setTime(new Date())
                      .setText(String.format("Report being generated by querying these Resource types: %s", String.join(",", resourceTypesToQuery)))
      );

      // Scoop the data for the patients and store it
      // TODO: Make way to skip query a flag when calling generate, not an API configuration
      if (config.isSkipQuery()) {
        logger.info("Skipping query and store");
        for (PatientOfInterestModel patient : generateReport.getReportContext().getPatientsOfInterest()) {
          if (patient.getReference() != null) {
            patient.setId(patient.getReference().replaceAll("^Patient/", ""));
          }
        }
      } else {
        // TODO: Create way to use same scoop function in ReportDataController
        this.queryAndStorePatientData(new ArrayList<>(resourceTypesToQuery), criteria, generateReport.getReportContext());
      }

      if (generateReport.getReportContext().getPatientCensusLists().size() < 1 || generateReport.getReportContext().getPatientCensusLists() == null) {
        String msg = "A census for the specified criteria was not found.";
        logger.error(msg);
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, msg);
      }

      this.eventService.triggerEvent(EventTypes.AfterPatientDataQuery, criteria, generateReport.getReportContext());

      this.getFhirDataProvider().audit(task,
              generateReport.getReportContext().getUser().getJwt(),
              FhirHelper.AuditEventTypes.InitiateQuery,
              "Successfully Initiated Query");

      // TODO: Is this the same thing added to the report context (a level up) already?
      generateReport.getReportContext().getMeasureContext().setReportId(
              ReportIdHelper.getMasterMeasureReportId(generateReport.getReportContext().getMasterIdentifier(), generateReport.getReportContext().getMeasureContext().getBundleId())
      );

      String reportAggregatorClassName = ApiUtility.getReportAggregatorClassName(config, generateReport.getLocationId());
      IReportAggregator reportAggregator = (IReportAggregator) context.getBean(Class.forName(reportAggregatorClassName));

      String measureGeneratorClassName = ApiUtility.getReportGeneratorClassName(config, generateReport.getLocationId());
      IMeasureReportGenerator measureGenerator = (IMeasureReportGenerator) context.getBean(Class.forName(measureGeneratorClassName));

      this.eventService.triggerEvent(EventTypes.BeforeMeasureEval,
              criteria,
              generateReport.getReportContext(),
              generateReport.getReportContext().getMeasureContext());

      measureGenerator.generate(this.stopwatchManager,
              generateReport.getReportContext(),
              generateReport.getReportContext().getMeasureContext(),
              criteria,
              config,
              generateReport.getReportContext().getUser(),
              reportAggregator);

      this.eventService.triggerEvent(EventTypes.AfterMeasureEval,
              criteria,
              generateReport.getReportContext(),
              generateReport.getReportContext().getMeasureContext());

      this.eventService.triggerEvent(EventTypes.BeforeReportStore,
              criteria,
              generateReport.getReportContext(),
              generateReport.getReportContext().getMeasureContext());

      measureGenerator.store(generateReport.getReportContext().getMeasureContext(), generateReport.getReportContext());

      this.eventService.triggerEvent(EventTypes.AfterReportStore,
              criteria,
              generateReport.getReportContext(),
              generateReport.getReportContext().getMeasureContext());

      this.stopwatchManager.print();
      this.stopwatchManager.reset();

      task.setStatus(Task.TaskStatus.COMPLETED);
      task.addNote(
              new Annotation()
                      .setTime(new Date())
                      .setText("Done generating report.")
      );
    } catch (Exception ex) {
      String errorMessage = String.format("Issue with report generation: (%s) %s", ex.getClass().getSimpleName(),ex.getMessage());
      logger.error(errorMessage);
      Annotation note = new Annotation();
      note.setText(errorMessage);
      note.setTime(new Date());
      task.addNote(note);
      task.setStatus(Task.TaskStatus.FAILED);
    } finally {
      task.setLastModified(new Date());
      dataProvider.updateResource(task);
    }
  }

}
