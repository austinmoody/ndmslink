package com.lantanagroup.link.api.controller;

import com.lantanagroup.link.Constants;
import com.lantanagroup.link.*;
import com.lantanagroup.link.auth.LinkCredentials;
import com.lantanagroup.link.config.datagovernance.DataGovernanceConfig;
import com.lantanagroup.link.config.query.USCoreConfig;
import com.lantanagroup.link.config.query.USCoreOtherResourceTypeConfig;
import com.lantanagroup.link.model.ExpungeResourcesToDelete;
import com.lantanagroup.link.model.Job;
import com.lantanagroup.link.model.UploadFile;
import lombok.Getter;
import lombok.Setter;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PreDestroy;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api")
public class ReportDataController extends BaseController {
  private static final Logger logger = LoggerFactory.getLogger(ReportDataController.class);

  // Instantiate an executor service
  private final ExecutorService executor = Executors.newSingleThreadExecutor();

  @Autowired
  @Setter
  private ApplicationContext context;

  @Autowired
  @Setter
  @Getter
  private DataGovernanceConfig dataGovernanceConfig;

  @Autowired
  private USCoreConfig usCoreConfig;

  // Disallow binding of sensitive attributes
  // Ex: DISALLOWED_FIELDS = new String[]{"details.role", "details.age", "is_admin"};
  final String[] DISALLOWED_FIELDS = new String[]{};

  @PreDestroy
  public void shutdown() {
    // needed to avoid resource leak
    executor.shutdown();
  }
  @InitBinder
  public void initBinder(WebDataBinder binder) {
    binder.setDisallowedFields(DISALLOWED_FIELDS);
  }

  @PostMapping(value="/data/file")
  public ResponseEntity<?> receiveFileData(@AuthenticationPrincipal LinkCredentials user,
                              HttpServletRequest request,
                                           @Valid @RequestBody UploadFile uploadFile,
                                           BindingResult bindingResult) {

    if (bindingResult.hasErrors()) {
      StringBuilder errorMessages = new StringBuilder();
      bindingResult.getAllErrors().forEach(error -> errorMessages.append(error.getDefaultMessage()).append("\n"));
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorMessages);
    }

    logger.info("Received UploadFile of Type '{}' and Name '{}'", uploadFile.getType(), uploadFile.getName());

    Task task = TaskHelper.getNewTask(user, Constants.FILE_UPLOAD);
    FhirDataProvider fhirDataProvider = getFhirDataProvider();
    fhirDataProvider.updateResource(task);
    Job job = new Job(task);

    // call processUploadFile
    executor.submit(() -> processUploadFile(user, uploadFile, task.getId()));

    return ResponseEntity.ok(job);
  }

  private void processUploadFile(LinkCredentials user, UploadFile uploadFile, String taskId) {

    logger.info("Processing UploadFile of type '{}' from source '{}'", uploadFile.getType(), uploadFile.getSource());

    // Default to generic source if not specific...
    if (uploadFile.getSource() == null || uploadFile.getSource().isEmpty()){
      uploadFile.setSource("generic");
    }

    boolean validDataProcessorConfig = config.ValidDataProcessor(uploadFile.getSource(), uploadFile.getType());
    if (!validDataProcessorConfig) {
      String errorMessage = "Data Processor configuration is invalid.  Check 'data-process' section of API configuration";
      logger.error(errorMessage);
      throw new IllegalStateException(errorMessage);
    }
    logger.info("Data Processor configuration is valid");

    // Get the task so that it can be updated later
    FhirDataProvider dataProvider = getFhirDataProvider();
    Task task = dataProvider.getTaskById(taskId);
    Annotation annotation = TaskHelper.getAnnotationFromString(String.format("File of type '%s' from source '%s'", uploadFile.getType(), uploadFile.getSource()));
    task.addNote(annotation);

    try {

      // Content should be Base64
      byte[] decodedContent = Base64.getDecoder().decode(uploadFile.getContent());
      logger.info("Decoded Uploaded File, byte size {}", decodedContent.length);

      // Get Processor Class Name
      HashMap<String, String> processorMapForSource = config.getDataProcessor().get(uploadFile.getSource());
      String dataProcessorClassName = processorMapForSource.get(uploadFile.getType());
      logger.info("Data Processor class that will be used: {}", dataProcessorClassName);

      Class<?> dataProcessorClass = Class.forName(dataProcessorClassName);
      IUploadFileToMeasureReport dataProcessor = (IUploadFileToMeasureReport) this.context.getBean(dataProcessorClass);

      logger.info("Starting Process of Uploaded File");
      MeasureReport measureReport = dataProcessor.convert(uploadFile, dataProvider);
      logger.info("Process of Uploaded File completed");

      // Update Task to complete
      task.setStatus(Task.TaskStatus.COMPLETED);

      // Add note about MeasureReport created with this upload
      String processNote = String.format("Upload File created MeasureReport with ID: %s", measureReport.getId());
      annotation = TaskHelper.getAnnotationFromString(processNote);
      task.addNote(annotation);
      logger.info(processNote);

      // Create Provenance to track downloaded file
      logger.info("Adding Provenance for downloaded file");
      Provenance provenance = ProvenanceHelper.getNewFileDownloadProvenance(user, Arrays.asList(measureReport, task), Constants.EXTERNAL_FILE_DOWNLOAD, uploadFile.getSource(), uploadFile.getType());
      dataProvider.createResource(provenance);

      // Add Provenance to Task
      Reference provenanceReference = new Reference();
      provenanceReference.setReference(String.format("%s/%s", provenance.getResourceType().name(), provenance.getIdElement().getIdPart()));
      task.setFor(provenanceReference);

    } catch (Exception ex) {
      String processNote = String.format("Issue With Upload File Processing: %s", ex.getMessage());
      logger.error(processNote);
      annotation = TaskHelper.getAnnotationFromString(processNote);
      task.addNote(annotation);
      task.setStatus(Task.TaskStatus.FAILED);
    } finally {
      task.setLastModified(new Date());
      dataProvider.updateResource(task);
    }

  }

  private void manualExpungeTask(LinkCredentials user, HttpServletRequest request, ExpungeResourcesToDelete resourcesToDelete, String taskId) {

    logger.info("Manual Expunge Started (Task ID: {})", taskId);

    FhirDataProvider fhirDataProvider = getFhirDataProvider();
    Task task = fhirDataProvider.getTaskById(taskId);

    try {
      for (String resourceIdentifier : resourcesToDelete.getResourceIdentifiers()) {
        try {
          fhirDataProvider.deleteResource(resourcesToDelete.getResourceType(), resourceIdentifier, true);
          getFhirDataProvider().audit(request,
                  user.getJwt(),
                  FhirHelper.AuditEventTypes.Delete,
                  String.format("Resource of Type '%s' with Id of '%s' has been expunged.", resourcesToDelete.getResourceType(), resourceIdentifier));
          logger.info("Removing Resource of type {} with Identifier {}", resourcesToDelete.getResourceType(), resourceIdentifier);
        } catch (Exception ex) {
          logger.info("Issue Removing Resource of type {} with Identifier {}", resourcesToDelete.getResourceType(), resourceIdentifier);
          throw ex;
        }
      }
      task.setStatus(Task.TaskStatus.COMPLETED);
    } catch (Exception ex) {
      logger.error("Manual Expunge Error - {} (Task ID: {}", ex.getMessage(), taskId);
      Annotation note = new Annotation();
      note.setTime(new Date());
      note.setText(String.format("Issue With Data Expunge: %s", ex.getMessage()));
      task.setNote(Arrays.asList(note));
      task.setStatus(Task.TaskStatus.FAILED);
    } finally {
      task.setLastModified(new Date());
      fhirDataProvider.updateResource(task);
    }

    logger.info("Manual Expunge Complete (Task ID: {})", taskId);

  }

  @PostMapping(value = "/data/manual-expunge")
  public ResponseEntity<?> manualExpunge(
          @AuthenticationPrincipal LinkCredentials user,
          HttpServletRequest request,
          @RequestBody ExpungeResourcesToDelete resourcesToDelete) throws Exception {

    Boolean hasExpungeRole = HasExpungeRole(user);

    if (!hasExpungeRole) {
      logger.error("User doesn't have proper role to expunge data");
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("User does not have proper role to expunge data.");
    }

    if (resourcesToDelete == null) {
      String errorMessage = "Payload not provided";
      logger.error(errorMessage);
      //throw new Exception();
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorMessage);
    } else if (resourcesToDelete.getResourceType() == null || resourcesToDelete.getResourceType().trim().isEmpty()) {
      String errorMessage = "Resource type to delete not specified";
      logger.error("Resource type to delete not specified");
      //throw new Exception();
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorMessage);
    } else if (resourcesToDelete.getResourceIdentifiers() == null || resourcesToDelete.getResourceIdentifiers().length == 0) {
      String errorMessage = "Resource Identifiers to delete not specified";
      logger.error("Resource Identifiers to delete not specified");
      //throw new Exception();
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorMessage);
    }

    Task task = TaskHelper.getNewTask(user, Constants.MANUAL_EXPUNGE);
    FhirDataProvider fhirDataProvider = getFhirDataProvider();
    fhirDataProvider.updateResource(task);
    Job job = new Job(task);

    executor.submit(() -> manualExpungeTask(user, request, resourcesToDelete,task.getId()));

    return ResponseEntity.ok(job);
  }

  private void expungeData(LinkCredentials user, HttpServletRequest request, String taskId) {

    logger.info("Data Expunge Started (Task ID: {})", taskId);

    // Get the task so that it can be updated later
    FhirDataProvider dataProvider = getFhirDataProvider();
    Task task = dataProvider.getTaskById(taskId);

    try {
      if (dataGovernanceConfig == null) {
        throw new Exception(String.format("API Data Governance Not Configured (Task ID %s)", taskId));
      }

      expungeCountByTypeAndRetentionAndPatientFilter(request,
              user,
              dataGovernanceConfig.getExpungeChunkSize(),
              "List",
              dataGovernanceConfig.getCensusListRetention(),
              false);

      expungeCountByTypeAndRetentionAndPatientFilter(request,
              user,
              dataGovernanceConfig.getExpungeChunkSize(),
              "Bundle",
              dataGovernanceConfig.getPatientDataRetention(),
              true);

      // This to remove the "placeholder" Patient resources
      expungeCountByTypeAndRetentionAndPatientFilter(request,
              user,
              dataGovernanceConfig.getExpungeChunkSize(),
              "Patient",
              dataGovernanceConfig.getPatientDataRetention(),
              false);

      // Remove individual MeasureReport tied to Patient
      // Individual MeasureReport for patient will be tagged.  Others have no PHI.
      expungeCountByTypeAndRetentionAndPatientFilter(request,
              user,
              dataGovernanceConfig.getExpungeChunkSize(),
              "MeasureReport",
              dataGovernanceConfig.getMeasureReportRetention(),
              true);

      // Loop uscore.patient-resource-types & other-resource-types and delete
      for (String resourceType : usCoreConfig.getPatientResourceTypes()) {
        expungeCountByTypeAndRetentionAndPatientFilter(request,
                user,
                dataGovernanceConfig.getExpungeChunkSize(),
                resourceType,
                dataGovernanceConfig.getResourceTypeRetention(),
                false);
      }

      for (USCoreOtherResourceTypeConfig otherResourceType : usCoreConfig.getOtherResourceTypes()) {
        expungeCountByTypeAndRetentionAndPatientFilter(request,
                user,
                dataGovernanceConfig.getExpungeChunkSize(),
                otherResourceType.getResourceType(),
                dataGovernanceConfig.getOtherTypeRetention(),
                false);
      }

      logger.info("Data Expunge Complete (Task ID: {})", taskId);

      task.setStatus(Task.TaskStatus.COMPLETED);

    } catch (Exception ex) {
      logger.error("Data Expunge Issue: {} (Task ID: {})", ex.getMessage(), taskId);
      Annotation note = new Annotation();
      note.setTime(new Date());
      note.setText(String.format("Issue With Data Expunge: %s", ex.getMessage()));
      task.setNote(Arrays.asList(note));
      task.setStatus(Task.TaskStatus.FAILED);
    } finally {
      task.setLastModified(new Date());
      dataProvider.updateResource(task);
    }

  }
  @DeleteMapping(value = "/data/expunge")
  public ResponseEntity<?> expungeData(@AuthenticationPrincipal LinkCredentials user,
                                   HttpServletRequest request) {

    Boolean hasExpungeRole = HasExpungeRole(user);

    if (!hasExpungeRole) {
      logger.error("User doesn't have proper role to expunge data");
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("User does not have proper role to expunge data.");
    }

    Task task = TaskHelper.getNewTask(user, Constants.EXPUNGE_TASK);
    FhirDataProvider fhirDataProvider = getFhirDataProvider();
    fhirDataProvider.updateResource(task);
    Job job = new Job(task);

    executor.submit(() -> expungeData(user, request, task.getId()));

    return ResponseEntity.ok(job);
  }

  private Date SubtractDurationFromNow(String retentionPeriod) throws DatatypeConfigurationException {

    Calendar rightNow = Calendar.getInstance();
    rightNow.setTime(new Date());

    Duration durationRetention = DatatypeFactory.newInstance().newDuration(retentionPeriod);

    // Subtract the duration from the current date
    rightNow.add(Calendar.YEAR, -durationRetention.getYears());
    rightNow.add(Calendar.MONTH, -durationRetention.getMonths());
    rightNow.add(Calendar.DAY_OF_MONTH, -durationRetention.getDays());
    rightNow.add(Calendar.HOUR_OF_DAY, -durationRetention.getHours());
    rightNow.add(Calendar.MINUTE, -durationRetention.getMinutes());
    rightNow.add(Calendar.SECOND, -durationRetention.getSeconds());

    return rightNow.getTime();
  }

  private void expungeCountByTypeAndRetentionAndPatientFilter(HttpServletRequest request, LinkCredentials user, Integer count, String resourceType, String retention, Boolean filterPatientTag) throws DatatypeConfigurationException {
    int bundleEntrySize = 1;
    int expunged = 0;
    Bundle bundle;
    FhirDataProvider fhirDataProvider = getFhirDataProvider();

    Date searchBeforeDate = SubtractDurationFromNow(retention);

    logger.info("Searching for {} last updated before {}, in chunks of {}", resourceType, searchBeforeDate, count);

    while (bundleEntrySize > 0) {

      if (filterPatientTag) {
        bundle = fhirDataProvider.getResourcesSummaryByCountTagLastUpdated(resourceType, count, Constants.MainSystem, Constants.patientDataTag, searchBeforeDate);
      } else {
        bundle = fhirDataProvider.getResourcesSummaryByCountLastUpdated(resourceType, count, searchBeforeDate);
      }

      if( (bundle != null) && (bundle.getEntry().size() > 0) ) {
        bundleEntrySize = bundle.getEntry().size();

        for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {

          expungeResourceById(entry.getResource().getIdElement().getIdPart(),
                  entry.getResource().getResourceType().toString(),
                  request,
                  user);

          expunged++;

        }
      } else {
        bundleEntrySize = 0;
      }

    }

    logger.info("Total {} {} found and expunged.", expunged, resourceType);

  }

  private void expungeResourceById(String id, String type, HttpServletRequest request, LinkCredentials user) {
    FhirDataProvider fhirDataProvider = getFhirDataProvider();
    try {
      fhirDataProvider.deleteResource(type, id, true);
      getFhirDataProvider().audit(request,
              user.getJwt(),
              FhirHelper.AuditEventTypes.Delete,
              String.format("Resource of Type '%s' with Id of '%s' has been expunged.", type, id));
      logger.info("Resource of Type '{}' with Id of '{}' has been expunged.", type, id);
    } catch (Exception ex) {
      logger.error("Issue Deleting Resource of Type '{}' with Id of '{}'", type, id);
    }
  }

  public Boolean HasExpungeRole(LinkCredentials user) {
    ArrayList<String> roles = (ArrayList<String>)user.getJwt().getClaim("realm_access").asMap().get("roles");

    boolean hasExpungeRole = false;
    for (String role : roles) {
      if (role.equals(dataGovernanceConfig.getExpungeRole())) {
        hasExpungeRole = true;
        break;
      }
    }

    return hasExpungeRole;

  }
}
