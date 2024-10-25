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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PreDestroy;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;


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
    List<PatientOfInterestModel> patientOfInterestModelList;

    // TODO: When would the following condition ever be true?
    //       In the standard report generation pipeline, census lists haven't been retrieved by the time we get here
    //       Are we guarding against a case where a BeforePatientOfInterestLookup handler might have done so?
    //       (But wouldn't it be more appropriate to plug that logic in as the patient ID resolver?)
    if (reportContext.getPatientCensusLists() != null && reportContext.getPatientCensusLists().size() > 0) {
      patientOfInterestModelList = new ArrayList<>();
      for (ListResource censusList : reportContext.getPatientCensusLists()) {
        for (ListResource.ListEntryComponent censusPatient : censusList.getEntry()) {
          PatientOfInterestModel patient = new PatientOfInterestModel(
                  censusPatient.getItem().getReference(),
                  IdentifierHelper.toString(censusPatient.getItem().getIdentifier()));
          patientOfInterestModelList.add(patient);
        }
      }
    } else {
      IPatientOfInterest provider;
      Class<?> patientIdResolverClass = Class.forName(this.config.getPatientIdResolver());
      Constructor<?> patientIdentifierConstructor = patientIdResolverClass.getConstructor();
      provider = (IPatientOfInterest) patientIdentifierConstructor.newInstance();
      provider.getPatientsOfInterest(criteria, reportContext, this.config);
    }
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
  public ResponseEntity<Object> newGenerateReport(@AuthenticationPrincipal LinkCredentials user,
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

  private void sendReport(LinkCredentials user,
                          @PathVariable String reportId,
                          HttpServletRequest request,
                          String taskId) {

    // Get the task so that it can be updated later
    FhirDataProvider dataProvider = getFhirDataProvider();
    Task task = dataProvider.getTaskById(taskId);

    try {

      logger.info("Sending Report with ID {}", reportId);

      String submitterName = FhirHelper.getName(user.getPractitioner().getName());

      DocumentReference documentReference = this.getFhirDataProvider().findDocRefForReport(reportId);
      String noteMessage = String.format("DocumentReference '%s' associated with report retrieved.", documentReference.getIdElement().getIdPart());
      logger.info(noteMessage);
      ApiUtility.addNoteToTask(task, noteMessage);

      List<MeasureReport> reports = documentReference.getIdentifier().stream()
              .map(identifier -> ReportIdHelper.getMasterMeasureReportId(reportId, identifier.getValue()))
              .map(id -> this.getFhirDataProvider().getMeasureReportById(id))
              .collect(Collectors.toList());

      Class<?> senderClazz = Class.forName(this.config.getSender());
      IReportSender sender = (IReportSender) this.context.getBean(senderClazz);
      logger.info("Report '{}' being sent using class '{}'", reportId, config.getSender());

      String sentLocation = sender.send(reports, documentReference, request, this.getFhirDataProvider(), bundlerConfig);
      noteMessage = String.format("Report with ID '%s' sent to %s", reportId, sentLocation);
      logger.info(noteMessage);
      ApiUtility.addNoteToTask(task, noteMessage);

      // Log / Add Task Note
      noteMessage = String.format("Report with ID %s submitted by %s on %s",
              documentReference.getMasterIdentifier().getValue(),
              (Helper.validateLoggerValue(submitterName) ? submitterName : ""),
              new Date());
      logger.info(noteMessage);
      ApiUtility.addNoteToTask(task, noteMessage);

      // Now that we've submitted (successfully), update the doc ref with the status and date
      documentReference.setDocStatus(DocumentReference.ReferredDocumentStatus.FINAL);
      documentReference.setDate(new Date());
      documentReference = FhirHelper.incrementMajorVersion(documentReference);
      this.getFhirDataProvider().updateResource(documentReference);
      noteMessage = String.format("DocumentReference '%s' updated with Status '%s', Date '%s', and Version '%s'",
              documentReference.getIdElement().getIdPart(),
              documentReference.getStatus(),
              documentReference.getDate(),
              documentReference.getExtensionByUrl(Constants.DOCUMENT_REFERENCE_VERSION_URL).getValue().toString());
      logger.info(noteMessage);
      ApiUtility.addNoteToTask(task, noteMessage);

      this.getFhirDataProvider().audit(task, user.getJwt(), FhirHelper.AuditEventTypes.Send, "Successfully Sent Report");

      //reportContext.getPatientCensusLists().get(0).getIdElement().getIdPart()
      task.setStatus(Task.TaskStatus.COMPLETED);
      ApiUtility.addNoteToTask(task, String.format("Done sending report '%s'", reportId));
    } catch (Exception ex) {
      String errorMessage = String.format("Issue with sending report '%s' - %s", reportId, ex.getMessage());
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

  /**
   * Sends the specified report to the recipients configured in <strong>api.send-urls</strong>
   */
  @PostMapping("/{reportId}/$send")
  public ResponseEntity<Object> send(
          @AuthenticationPrincipal LinkCredentials user,
          @PathVariable String reportId,
          HttpServletRequest request){

    if (StringUtils.isEmpty(this.config.getSender())) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Not configured for sending");
    }

    Task task = TaskHelper.getNewTask(user, request, Constants.SEND_REPORT);
    FhirDataProvider fhirDataProvider = getFhirDataProvider();
    fhirDataProvider.updateResource(task);

    Job job = new Job(task);

    executor.submit(() -> sendReport(user, reportId, request, task.getId()));

    return ResponseEntity.ok(job);
  }

  @GetMapping("/{reportId}/$download/{type}")
  public void download(
          @PathVariable String reportId,
          @PathVariable String type,
          HttpServletResponse response,
          @AuthenticationPrincipal LinkCredentials user,
          HttpServletRequest request) {

    // TODO: Austin to verify this after getting new reports to generate

    Task task = TaskHelper.getNewTask(user, request, Constants.REPORT_DOWNLOAD);
    FhirDataProvider fhirDataProvider = getFhirDataProvider();

    try {

      if (StringUtils.isEmpty(this.config.getDownloader()))
        throw new IllegalStateException("Not configured for downloading");

      IReportDownloader downloader;
      Class<?> downloaderClass = Class.forName(this.config.getDownloader());
      Constructor<?> downloaderCtor = downloaderClass.getConstructor();
      downloader = (IReportDownloader) downloaderCtor.newInstance();

      downloader.download(reportId, type, this.getFhirDataProvider(), response, this.ctx, this.bundlerConfig, this.eventService);

      this.getFhirDataProvider().audit(task, user.getJwt(), FhirHelper.AuditEventTypes.Export, "Successfully Exported Report for Download");

    } catch (Exception ex) {
      String errorMessage = String.format("Issue with download: %s", ex.getMessage());
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

  @GetMapping(value = "/{reportId}")
  public ReportModel getReport(
          @PathVariable("reportId") String reportId) {

    ReportModel reportModel = new ReportModel();
    List<ReportModel.ReportMeasure> reportModelList = new ArrayList<>();
    reportModel.setReportMeasureList(reportModelList);
    DocumentReference documentReference = this.getFhirDataProvider().findDocRefForReport(reportId);

    for (int i = 0; i < documentReference.getIdentifier().size(); i++) {
      String encodedReport = "";
      //prevent injection from reportId parameter
      try {
        encodedReport = Helper.encodeForUrl(ReportIdHelper.getMasterMeasureReportId(reportId, documentReference.getIdentifier().get(i).getValue()));
      } catch (Exception ex) {
        logger.error(ex.getMessage());
      }

      // Get the Measure to put in Report from Evaluation Service, where it is actually being used
      // and not the old version pulled from report defs in the config
      FhirDataProvider evaluationService = new FhirDataProvider(this.config.getEvaluationService());
      Measure measure = evaluationService.getMeasureById(documentReference.getIdentifier().get(i).getValue());

      ReportModel.ReportMeasure reportMeasure = new ReportModel.ReportMeasure();
      // get Master Measure Report
      reportMeasure.setMeasureReport(this.getFhirDataProvider().getMeasureReportById(encodedReport));
      reportMeasure.setBundleId(measure.getIdElement().getIdPart());
      reportMeasure.setMeasure(measure);
      reportModel.setVersion(documentReference
              .getExtensionByUrl(Constants.DocumentReferenceVersionUrl) != null ?
              documentReference.getExtensionByUrl(Constants.DocumentReferenceVersionUrl).getValue().toString() : null);
      reportModel.setStatus(documentReference.getDocStatus().toString());
      reportModel.setDate(documentReference.getDate());
      reportModelList.add(reportMeasure);
      reportModel.setReportPeriodStart(documentReference.getContext().getPeriod().getStart());
      reportModel.setReportPeriodEnd(documentReference.getContext().getPeriod().getEnd());
    }
    return reportModel;
  }

  @GetMapping(produces = {MediaType.APPLICATION_JSON_VALUE})
  public ReportBundle searchReports(
          @AuthenticationPrincipal LinkCredentials user,
          HttpServletRequest request,
          @RequestParam(required = false, defaultValue = "1") Integer page,
          @RequestParam(required = false) String author,
          @RequestParam(required = false) String identifier,
          @RequestParam(required = false) String periodStartDate,
          @RequestParam(required = false) String periodEndDate,
          @RequestParam(required = false) String docStatus,
          @RequestParam(required = false) String submittedDate,
          @RequestParam(required = false) String submitted) {

    Task task = TaskHelper.getNewTask(user, request, Constants.REPORT_SEARCH);
    FhirDataProvider fhirDataProvider = getFhirDataProvider();

    ReportBundle reportBundle = new ReportBundle();

    try {
      Bundle bundle;
      boolean andCond = false;

      String url = this.config.getDataStore().getBaseUrl();
      if (!url.endsWith("/")) url += "/";
      url += "DocumentReference?";
      if (author != null) {

        Annotation note = new Annotation();
        note.setText(String.format("Report search parameter author = %s",  author));
        note.setTime(new Date());
        task.addNote(note);

        url += "author=" + author;
        andCond = true;
      }
      if (identifier != null) {

        Annotation note = new Annotation();
        note.setText(String.format("Report search parameter identifier = %s",  identifier));
        note.setTime(new Date());
        task.addNote(note);

        if (andCond) {
          url += "&";
        }
        url += "identifier=" + Helper.URLEncode(identifier);
        andCond = true;
      }
      if (periodStartDate != null) {

        Annotation note = new Annotation();
        note.setText(String.format("Report search parameter period start date = %s",  periodStartDate));
        note.setTime(new Date());
        task.addNote(note);

        if (andCond) {
          url += "&";
        }
        url += PERIOD_START_PARAM_NAME + "=ge" + periodStartDate;
        andCond = true;
      }
      if (periodEndDate != null) {

        Annotation note = new Annotation();
        note.setText(String.format("Report search parameter period end date = %s",  periodEndDate));
        note.setTime(new Date());
        task.addNote(note);

        if (andCond) {
          url += "&";
        }
        url += PERIOD_END_PARAM_NAME + "=le" + periodEndDate;
        andCond = true;
      }
      if (docStatus != null) {

        Annotation note = new Annotation();
        note.setText(String.format("Report search parameter document status = %s",  docStatus));
        note.setTime(new Date());
        task.addNote(note);

        if (andCond) {
          url += "&";
        }
        url += "docStatus=" + docStatus.toLowerCase();
        andCond = true;
      }

      Boolean submittedBoolean = null;
      if (submitted != null) {
        submittedBoolean = Boolean.parseBoolean(submitted);

        Annotation note = new Annotation();
        note.setText(String.format("Report search parameter submitted = %s",  submittedBoolean));
        note.setTime(new Date());
        task.addNote(note);

      }

      if (Boolean.TRUE.equals(submittedBoolean)) {
        // We want to find documents that have been submitted.  Which
        // should mean that docStatus = final and the date isn't null.
        // All we can do here is search for docStatus = final then later
        // also verify that the date has a value.
        if (andCond) {
          url += "&";
        }
        url += "docStatus=final";
        andCond = true;
      } else if (Boolean.FALSE.equals(submittedBoolean)) {
        // We want to fnd documents that HAVE NOT been submitted.  Which
        // should mean that docStatus <> final and that the date field is
        // either missing or set to null.  Which we have to check later.
        if (andCond) {
          url += "&";
        }
        url += "_filter=docStatus+ne+final";
        andCond = true;
      }

      if (submittedDate != null) {

        Annotation note = new Annotation();
        note.setText(String.format("Report search parameter submitted date = %s",  submittedDate));
        note.setTime(new Date());
        task.addNote(note);

        if (andCond) {
          url += "&";
        }
        Date submittedDateAsDate = Helper.parseFhirDate(submittedDate);
        Date theDayAfterSubmittedDateEnd = Helper.addDays(submittedDateAsDate, 1);
        String theDayAfterSubmittedDateEndAsString = Helper.getFhirDate(theDayAfterSubmittedDateEnd);
        url += "date=ge" + submittedDate + "&date=le" + theDayAfterSubmittedDateEndAsString;
      }

      bundle = this.getFhirDataProvider().fetchResourceFromUrl(url);
      List<Report> lst = bundle.getEntry().parallelStream().map(Report::new).collect(Collectors.toList());

      // Remove items from lst if we are searching for submitted only but the date is null
      // Only DocumentReferences that have been submitted will have a value for date.
      if (Boolean.TRUE.equals(submittedBoolean)) {
        lst.removeIf(report -> report.getSubmittedDate() == null);
      }

      // Remove items from lst if we are searching for non-submitted but the date
      // has a value.  Only DocumentReference that have been submitted will have a value for
      // date
      if (Boolean.FALSE.equals(submittedBoolean)) {
        lst.removeIf(report -> report.getSubmittedDate() != null);
      }

      List<String> reportIds = lst.stream().map(report -> ReportIdHelper.getMasterMeasureReportId(report.getId(), report.getReportMeasure().getValue())).collect(Collectors.toList());
      Bundle response = this.getFhirDataProvider().getMeasureReportsByIds(reportIds);

      response.getEntry().parallelStream().forEach(bundleEntry -> {
        if (bundleEntry.getResource().getResourceType().equals(ResourceType.MeasureReport)) {
          MeasureReport measureReport = (MeasureReport) bundleEntry.getResource();
          Extension extension = measureReport.getExtensionByUrl(Constants.NotesUrl);
          Report foundReport = lst.stream().filter(rep -> rep.getId().equals(measureReport.getIdElement().getIdPart().split("-")[0])).findAny().orElse(null);
          if (extension != null && foundReport != null) {
            foundReport.setNote(extension.getValue().toString());
          }
        }
      });
      reportBundle.setReportTypeId(bundle.getId());
      reportBundle.setList(lst);
      reportBundle.setTotalSize(bundle.getTotal());

      task.setStatus(Task.TaskStatus.COMPLETED);

      this.getFhirDataProvider().audit(task, user.getJwt(), FhirHelper.AuditEventTypes.SearchReports, "Successfully Searched Reports");

    } catch (Exception ex) {
      String errorMessage = String.format("Issue with searching reports: %s", ex.getMessage());
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

    return reportBundle;
  }
}
