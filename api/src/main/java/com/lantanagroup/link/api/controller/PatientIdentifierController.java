package com.lantanagroup.link.api.controller;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.AdditionalRequestHeadersInterceptor;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import com.google.common.annotations.VisibleForTesting;
import com.lantanagroup.link.Constants;
import com.lantanagroup.link.*;
import com.lantanagroup.link.api.ApiUtility;
import com.lantanagroup.link.config.api.PatientListReportingPeriods;
import com.lantanagroup.link.auth.LinkCredentials;
import com.lantanagroup.link.config.api.ApiConfig;
import com.lantanagroup.link.config.api.PatientListPullConfig;
import com.lantanagroup.link.config.query.QueryConfig;
import com.lantanagroup.link.config.query.USCoreConfig;
import com.lantanagroup.link.model.CsvEntry;
import com.lantanagroup.link.model.Job;
import com.lantanagroup.link.query.auth.EpicAuthConfig;
import com.lantanagroup.link.query.auth.HapiFhirAuthenticationInterceptor;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PreDestroy;
import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Reportable Response Controller
 */
@RestController
@RequestMapping("/api/poi")
public class PatientIdentifierController extends BaseController {
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private static final Logger logger = LoggerFactory.getLogger(PatientIdentifierController.class);
  private final ApiConfig apiConfig;
  private final QueryConfig queryConfig;
  private final USCoreConfig usCoreConfig;
  private final EpicAuthConfig epicAuthConfig;

  public PatientIdentifierController(ApiConfig apiConfig, QueryConfig queryConfig, USCoreConfig usCoreConfig, EpicAuthConfig epicAuthConfig) {
    super();
    this.apiConfig = apiConfig;
    this.queryConfig = queryConfig;
    this.usCoreConfig = usCoreConfig;
    this.epicAuthConfig = epicAuthConfig;
  }

  @PreDestroy
  public void shutdown() {
    // needed to avoid resource leak
    executor.shutdown();
  }

  /**
   * Posts a csv file with Patient Identifiers and Dates to the Fhir server.
   * @param csvContent The content of the CSV
   * @param listIdentifier - the type of the report (ex covid-min) and the format should be system|value
   */
  @PostMapping(value = "/csv", consumes = "text/csv")
  public void storeCSV(
          @RequestBody() String csvContent,
          @RequestParam String listIdentifier) throws Exception {
    logger.debug("Receiving Patient List CSV. Parsing...");
    if (listIdentifier == null || listIdentifier.isBlank()) {
      String msg = "List Identifier should be provided.";
      logger.error(msg);
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }
    if (!listIdentifier.contains("|")) {
      String msg = "List Identifier should be of format: system|value";
      logger.error(msg);
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }
    List<CsvEntry> list = this.getCsvEntries(csvContent);
    Map<String, List<CsvEntry>> csvMap = list.stream().collect(Collectors.groupingBy(CsvEntry::getPeriodIdentifier));
    for (String key : csvMap.keySet()) {
      ListResource listResource = getListResource(listIdentifier, csvMap.get(key));
      checkMeasureIdentifier(listResource);
      this.receiveFHIR(listResource);
    }
  }

  @PostMapping(value = "/fhir/List", consumes = {MediaType.APPLICATION_XML_VALUE})
  public void getPatientIdentifierListXML(
          @RequestBody() String body) throws Exception {
    logger.debug("Receiving patient identifier FHIR List in XML");

    ListResource list = this.ctx.newXmlParser().parseResource(ListResource.class, body);
    checkMeasureIdentifier(list);
    this.receiveFHIR(list);
  }

  @PostMapping(value = "/fhir/List", consumes = MediaType.APPLICATION_JSON_VALUE)
  public void getPatientIdentifierListJSON(
          @RequestBody() String body) throws Exception {
    logger.debug("Receiving patient identifier FHIR List in JSON");

    ListResource list = this.ctx.newJsonParser().parseResource(ListResource.class, body);
    checkMeasureIdentifier(list);
    this.receiveFHIR(list);
  }

  @PostMapping(value = "/fhir/PatientList", consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
  public ResponseEntity<?> getPatientIdentifierList(
          @AuthenticationPrincipal LinkCredentials user,
          HttpServletRequest request,
          @RequestBody() String body,
          @RequestHeader("Content-Type") String contentType
  ) {

    Task task = TaskHelper.getNewTask(user, request, Constants.REFRESH_PATIENT_LIST);
    FhirDataProvider fhirDataProvider = getFhirDataProvider();
    fhirDataProvider.updateResource(task);
    Job job = new Job(task);

    executor.submit(() -> processPatientIdentifierListTask(body, contentType, task.getId()));

    return ResponseEntity.ok(job);
  }

  @PostMapping("/patient-list-pull/{locationId}")
  public ResponseEntity<Job> patientListPull(@AuthenticationPrincipal LinkCredentials user,
                                         HttpServletRequest request,
                                         @PathVariable String locationId) {

    Task task = TaskHelper.getNewTask(user, request, Constants.PATIENT_LIST_PULL);
    FhirDataProvider fhirDataProvider = getFhirDataProvider();

    try {

      ApiUtility.addNoteToTask(task,
              String.format("Verifying Location '%s'",locationId)
      );

      // First check to see if we can find this Location on the DataStore, we will ultimately
      // add this as a Reference to the List resource.
      Location location = ApiUtility.getLocationFromDataStore(apiConfig.getDataStore(), locationId);
      // Verify that the location has Latitude/Longitude information that will ultimately be
      // required for ArcGIS
      if (!ApiUtility.locationHasPosition(location)) {
        throw new FHIRException(
                String.format("Location '%s' does not have necessary position coordinates", locationId)
        );
      }

      ApiUtility.addNoteToTask(task,
              String.format("Starting Patient List Pull for Location: %s", locationId)
      );

      fhirDataProvider.updateResource(task);

      executor.submit(
              () -> patientListPull(user, task.getId(), location)
      );

      // TODO: Add FHIR Audit

    } catch (Exception ex) {
      String errorMessage = String.format("Issue with starting patient list pull API call: %s", ex.getMessage());
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

  private void patientListPull(LinkCredentials user, String taskId, Location location) {
    // Get the task so that it can be updated later
    FhirDataProvider fhirDataProvider = getFhirDataProvider();
    Task task = fhirDataProvider.getTaskById(taskId);

    try {

      String locationId = location.getIdElement().getIdPart();

      Optional<PatientListPullConfig> patientListPullConfig = apiConfig.getPatientListPull().stream()
              .filter(
                      plp -> plp.getPatientListLocation().equals(locationId)
              ).findFirst();

      if (patientListPullConfig.isEmpty()) {
        throw new IllegalStateException(String.format("API Configuration missing patient list for Location %s", locationId));
      }

      PatientListPullConfig patientListConfig = patientListPullConfig.get();

      task.addNote(
              new Annotation()
                      .setText(String.format("List Identifier: %s", patientListConfig.getPatientListIdentifier()))
                      .setTime(new Date())
      );

      ListResource listFromEpic = pullListFromEpic(locationId, patientListConfig.getPatientListIdentifier());

      ApiUtility.addNoteToTask(task,
              String.format("List '%s' pulled with %d entries",
                      listFromEpic.getTitle(),
                      listFromEpic.getEntry().size())
      );

      ListResource listToSave = transformEpicList(listFromEpic, patientListConfig.getPatientListReportingPeriod(), location);

      // Save List To Data Store
      receiveFHIR(listToSave);

      task.addNote(
              new Annotation()
                      .setText("Patient List Pull Complete")
                      .setTime(new Date())
      );
      task.setStatus(Task.TaskStatus.COMPLETED);

    } catch (Exception ex) {
      String errorMessage = String.format("Issue with patientListPull: %s", ex.getMessage());
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

  private ListResource transformEpicList(ListResource sourceList, PatientListReportingPeriods reportingPeriod, Location location) throws URISyntaxException {
    ListResource target = new ListResource();

    String locationId = location.getIdElement().getIdPart();

    Period period = new Period();
    if (reportingPeriod == null) {
      reportingPeriod = PatientListReportingPeriods.Day;
    }

    if (reportingPeriod.equals(PatientListReportingPeriods.Month)) {
      period
              .setStart(Helper.getStartOfMonth(sourceList.getDate()))
              .setEnd(Helper.getEndOfMonth(sourceList.getDate(), 0));
    } else if (reportingPeriod.equals(PatientListReportingPeriods.Day)) {
      period
              .setStart(Helper.getStartOfDay(sourceList.getDate()))
              .setEnd(Helper.getEndOfDay(sourceList.getDate(), 0));
    }

    target.addExtension(Constants.ApplicablePeriodExtensionUrl, period);
    target.addIdentifier()
            .setSystem(Constants.MainSystem)
            .setValue(locationId);
    target.setStatus(ListResource.ListStatus.CURRENT);
    target.setMode(ListResource.ListMode.WORKING);
    target.setTitle(String.format("Patient List for %s", locationId));
    target.setCode(sourceList.getCode());
    target.setDate(sourceList.getDate());
    target.setSubjectTarget(location);

    URI baseUrl = new URI(usCoreConfig.getFhirServerBase());
    for (ListResource.ListEntryComponent sourceEntry : sourceList.getEntry()) {
      target.addEntry(transformListEntry(sourceEntry, baseUrl));
    }

    return target;
  }

  private ListResource.ListEntryComponent transformListEntry(ListResource.ListEntryComponent source, URI baseUrl)
          throws URISyntaxException {
    ListResource.ListEntryComponent target = source.copy();
    if (target.getItem().hasReference()) {
      URI referenceUrl = new URI(target.getItem().getReference());
      if (referenceUrl.isAbsolute()) {
        target.getItem().setReference(baseUrl.relativize(referenceUrl).toString());
      }
    }
    return target;
  }

  private ListResource pullListFromEpic(String locationId, String patientListId) throws Exception {
    // This will do the work of authenticating using our EPIC App client id & key, against the EPIC system's
    // oauth endpoint.  The interceptor is to be registered with the FHIR client to make calls to pull
    // resources from the EPIC system.
    HapiFhirAuthenticationInterceptor interceptor = new HapiFhirAuthenticationInterceptor(queryConfig, epicAuthConfig);
    AdditionalRequestHeadersInterceptor headersInterceptor = new AdditionalRequestHeadersInterceptor();
    headersInterceptor.addHeaderValue("Accept","application/json");

    FhirContext fhirContext = FhirContextProvider.getFhirContext();
    IGenericClient fhirClient = fhirContext.newRestfulGenericClient(usCoreConfig.getFhirServerBase());
    fhirClient.registerInterceptor(interceptor);
    fhirClient.registerInterceptor(headersInterceptor);

    // If a List doesn't exist on the server with the specified ID a
    // 404 "ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException" will be thrown
    ListResource r = fhirClient.fetchResourceFromUrl(ListResource.class,
            String.format("List/%s",patientListId));
    if (r == null) {
      throw new ResourceNotFoundException(
              String.format("Issue pulling List ID '%s' from Location '%s'",
                      patientListId,
                      locationId)
      );
    }

    return r;
  }

  private void processPatientIdentifierListTask(String receivedBody, String receivedType, String taskId) {

    // Get the task so that it can be updated later
    FhirDataProvider dataProvider = getFhirDataProvider();
    Task task = dataProvider.getTaskById(taskId);

    try {
      logger.info("Patient List Processing Started (Task ID: {})", taskId);

      IParser parser;
      if (receivedType.startsWith(MediaType.APPLICATION_JSON_VALUE)) {
        parser = ctx.newJsonParser();
      } else if (receivedType.startsWith(MediaType.APPLICATION_XML_VALUE)) {
        parser = ctx.newXmlParser();
      } else {
        throw new Exception("Received payload isn't JSON or XML");
      }

      ListResource list = parser.parseResource(ListResource.class, receivedBody);
      checkListLocation(list);
      receiveFHIR(list);

      logger.info("Patient List Processing Complete (Task ID: {})", taskId);

      task.setStatus(Task.TaskStatus.COMPLETED);
      Annotation note = new Annotation();
      note.setTime(new Date());
      note.setText(String.format("Patient List with %s entries has been stored.", list.getEntry().size()));
      task.addNote(note);

    } catch (Exception ex) {
      logger.error("Patient List Processing Issue: {} (Task ID: {})", ex.getMessage(), taskId);
      Annotation note = new Annotation();
      note.setTime(new Date());
      note.setText(String.format("Issue With Patient List Processing: %s", ex.getMessage()));
      task.setNote(List.of(note));
      task.setStatus(Task.TaskStatus.FAILED);
    } finally {
      task.setLastModified(new Date());
      dataProvider.updateResource(task);
    }
  }

  private void checkListLocation(ListResource listResource) {

    // The RefreshPatientList CLI adds an identifier to the FHIR List with the
    // Constants.MainSystem system + the patient-list-location value which is
    // configured in the cli-config.yml.  Here we attempt to pull that Location
    // from the Data Store to make sure it's there.

    if (listResource.getIdentifier().isEmpty()) {
      String errorMessage = String.format("Patient List %s is missing identifier", listResource.getIdentifier());
      logger.error(errorMessage);
      throw new FHIRException(errorMessage);
    }

    Identifier listLocationIdentifier = null;
    for (Identifier identifier : listResource.getIdentifier()) {
      if (identifier.getSystem().equals(Constants.MainSystem)) {
        listLocationIdentifier = identifier;
      }
    }
    if (listLocationIdentifier == null) {
      String errorMessage = String.format("Patient List %s is missing identifier associated with system '%s'", listResource.getId(), Constants.MainSystem);
      logger.error(errorMessage);
      throw new FHIRException(errorMessage);
    }

    FhirDataProvider dataStore = new FhirDataProvider(config.getDataStore());
    Location listLocation = dataStore.getLocationById(listLocationIdentifier.getValue());
    if (listLocation == null) {
      String errorMessage = String.format("List Location with id '%s' was not found on Data Store '%s'", listLocationIdentifier.getValue(), config.getDataStore().getBaseUrl());
      logger.error(errorMessage);
      throw new FHIRException(errorMessage);
    }
  }

  // TODO: Remove this
  private void checkMeasureIdentifier(ListResource list) {
    if (list.getIdentifier().isEmpty()) {
      String msg = "Census list should have an identifier.";
      logger.error(msg);
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }
    Identifier measureIdentifier = list.getIdentifier().get(0);
    FhirDataProvider evaluationDataProvider = new FhirDataProvider(this.config.getEvaluationService());
    Measure measure = evaluationDataProvider.findMeasureByIdentifier(measureIdentifier);
    if (measure == null) {
      String msg = String.format("Measure Identified With Value '%s' and System '%s' not found on CQF Evaluation Service", measureIdentifier.getValue(), measureIdentifier.getSystem());
      logger.error(msg);
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, msg);
    }
  }

  @VisibleForTesting
  List<CsvEntry> getCsvEntries(String csvContent) throws IOException, CsvValidationException {
    InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));
    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
    CSVReader csvReader = new CSVReaderBuilder(bufferedReader).withSkipLines(1).build();
    List<CsvEntry> list = new ArrayList<>();
    String[] line;
    while ((line = csvReader.readNext()) != null) {
      if (line.length > 0) {
        if (line[0] == null || line[0].isBlank()) {
          String msg = "Patient Identifier is required in CSV census import.";
          logger.error(msg);
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
        }
        if (!line[0].contains("|")) {
          String msg = "Patient Identifier in CSV census import should be of format: system|value";
          logger.error(msg);
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
        }
        if (line[1] == null || line[1].isBlank()) {
          String msg = "Start date is required in CSV census import.";
          logger.error(msg);
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
        }
        SimpleDateFormat formatStartDate = new SimpleDateFormat("yyyy-MM-dd");
        try {
          formatStartDate.setLenient(false);
          formatStartDate.parse(line[1]);
        } catch (ParseException ex) {
          String msg = "Invalid start date in CSV census import. The start date format should be: YYYY-mm-dd.";
          logger.error(msg);
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
        }
        if (line[2] == null || line[1].isBlank()) {
          String msg = "End date is required in CSV census import.";
          logger.error(msg);
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
        }
        SimpleDateFormat formatEndDate = new SimpleDateFormat("yyyy-MM-dd");
        try {
          formatEndDate.setLenient(false);
          formatEndDate.parse(line[2]);
        } catch (ParseException ex) {
          String msg = "Invalid end date format in CSV census import. The end date format should be: YYYY-mm-dd.";
          logger.error(msg);
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
        }
        CsvEntry entry = new CsvEntry(line[0], line[1], line[2], line[3]);
        list.add(entry);
      }
    }
    if (list.isEmpty()) {
      String msg = "The file should have at least one entry with data in CSV census import.";
      logger.error(msg);
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }
    //list.setExtension();
    return list;
  }

  private void receiveFHIR(Resource resource) throws Exception {
    // TODO: Refactor this legacy eventually.  It is set to only ever receive List
    logger.info("Storing patient identifiers");
    resource.setId((String) null);

    if (resource instanceof ListResource) {
      ListResource list = (ListResource) resource;

      List<Identifier> identifierList = list.getIdentifier();

      if (identifierList.isEmpty()) {
        String msg = "Census List is missing identifier";
        logger.error(msg);
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
      }

      // TODO: Call checkMeasureIdentifier here; remove the calls in getPatientIdentifierListXML/JSON

      Extension applicablePeriodExt = list.getExtensionByUrl(Constants.ApplicablePeriodExtensionUrl);

      if (applicablePeriodExt == null) {
        String msg = "Census list applicable-period extension is required";
        logger.error(msg);
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
      }

      Period applicablePeriod = applicablePeriodExt.getValue().castToPeriod(applicablePeriodExt.getValue());

      if (applicablePeriod == null) {
        String msg = "applicable-period extension must have a value of type Period";
        logger.error(msg);
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
      }

      if (!applicablePeriod.hasStart()) {
        String msg = "applicable-period.start must have start";
        logger.error(msg);
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
      }

      if (!applicablePeriod.hasEnd()) {
        String msg = "applicable-period.start must have end";
        logger.error(msg);
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
      }

      //system and value represents the measure intended for this patient id list
      String system = identifierList.get(0).getSystem();
      String value = identifierList.get(0).getValue();

      // TODO: Refactor - this all seems to exist to do nothing but reformat the dates in
      // the applicable extension... just set them the right way earlier in the List
      // pull process?
      DateTimeType startDate = applicablePeriod.getStartElement();
      DateTimeType endDate = applicablePeriod.getEndElement();
      String start = Helper.getFhirDate(LocalDateTime.of(startDate.getYear(), startDate.getMonth() + 1, startDate.getDay(), startDate.getHour(), startDate.getMinute(), startDate.getSecond()));
      String end = Helper.getFhirDate(LocalDateTime.of(endDate.getYear(), endDate.getMonth() + 1, endDate.getDay(), endDate.getHour(), endDate.getMinute(), endDate.getSecond()));

      Bundle bundle = this.getFhirDataProvider().findListByIdentifierAndDate(system, value, start, end);

      if (bundle.getEntry().isEmpty()) {
        applicablePeriod.setStartElement(new DateTimeType(start));
        applicablePeriod.setEndElement(new DateTimeType(end));
        this.getFhirDataProvider().createResource(list);
      } else {
        ListResource existingList = (ListResource) bundle.getEntry().get(0).getResource();
        FhirHelper.mergeCensusLists(existingList, list);
        this.getFhirDataProvider().updateResource(existingList);
      }
    } else {
      String msg = "Only \"List\" resources are allowed";
      logger.error(msg);
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    } /* else {
      this.getFhirDataProvider().createResource(resource);
    } */
  }

  private ListResource getListResource(String reportTypeId, List<CsvEntry> csvList) {
    ListResource list = new ListResource();
    list.addIdentifier(IdentifierHelper.fromString(reportTypeId));
    List<Extension> applicablePeriodExtensionUrl = new ArrayList<>();
    applicablePeriodExtensionUrl.add(new Extension(Constants.ApplicablePeriodExtensionUrl));
    applicablePeriodExtensionUrl.get(0).setValue(csvList.get(0).getPeriod());
    list.setExtension(applicablePeriodExtensionUrl);
    //list.setDateElement(new DateTimeType(listDate));
    list.setStatus(ListResource.ListStatus.CURRENT);
    list.setMode(ListResource.ListMode.WORKING);
    list.setTitle(String.format("Census List for %s", IdentifierHelper.fromString(reportTypeId).getValue()));

    CodeableConcept cc = new CodeableConcept();
    cc.setText("PatientList");
    list.setCode(cc);

    list.setDate(new Date());

    csvList.stream().parallel().forEach(csvEntry -> {
      ListResource.ListEntryComponent listEntry = new ListResource.ListEntryComponent();
      Reference reference = new Reference();
      if (csvEntry.getPatientLogicalID() != null && !csvEntry.getPatientLogicalID().isBlank()) {
        reference.setReference("Patient/" + csvEntry.getPatientLogicalID());
      }
      reference.setIdentifier(IdentifierHelper.fromString(csvEntry.getPatientIdentifier()));
      listEntry.setItem(reference);

      list.addEntry(listEntry);
    });
    return list;
  }
}
