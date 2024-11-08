package com.lantanagroup.link;

import org.hl7.fhir.r4.model.Coding;

public class Constants {

  private Constants() {
    throw new IllegalStateException("Utility class");
  }

  public static final String MAIN_SYSTEM = "https://nhsnlink.org";
  public static final String MHL_SYSTEM = "https://mhl.lantanagroup.com";
  public static final String REPORT_DEFINITION_TAG = "report-definition";
  public static final String REPORT_POSITION_EXT_URL = "https://hl7.org/fhir/uv/saner/StructureDefinition/GeoLocation";
  public static final String LINK_USER_TAG = "link-user";
  public static final String ROLES = "roles";
  public static final String FHIR_RESOURCES_PACKAGE_NAME = "org.hl7.fhir.r4.model.";
  public static final String UUID_PREFIX = "urn:uuid:";
  public static final String APPLICABLE_PERIOD_EXTENSION_URL = "https://www.lantanagroup.com/fhir/StructureDefinition/link-patient-list-applicable-period";
  public static final String QI_CORE_ORGANIZATION_PROFILE_URL = "https://hl7.org/fhir/us/qicore/StructureDefinition/qicore-organization";
  public static final String QI_CORE_PATIENT_PROFILE_URL = "https://hl7.org/fhir/us/qicore/StructureDefinition/qicore-patient";
  public static final String US_CORE_ENCOUNTER_PROFILE_URL = "https://hl7.org/fhir/us/core/StructureDefinition/us-core-encounter";
  public static final String US_CORE_MEDICATION_REQUEST_PROFILE_URL = "https://hl7.org/fhir/us/core/StructureDefinition/us-core-medicationrequest";
  public static final String US_CORE_MEDICATION_PROFILE_URL = "https://hl7.org/fhir/us/core/StructureDefinition/us-core-medication";
  public static final String US_CORE_CONDITION_PROFILE_URL = "https://hl7.org/fhir/us/core/StructureDefinition/us-core-condition";
  public static final String US_CORE_OBSERVATION_PROFILE_URL = "https://hl7.org/fhir/us/core/StructureDefinition/us-core-observation-lab";
  public static final String REPORT_BUNDLE_PROFILE_URL = "https://lantanagroup.com/fhir/nhsn-measures/StructureDefinition/nhsn-measurereport-bundle";
  public static final String MHL_REPORT_BUNDLE_PROFILE_URL = "https://lantanagroup.com/fhir/nih-measures/StructureDefinition/nih-measurereport-bundle";
  public static final String NATIONAL_PROVIDER_IDENTIFIER_SYSTEM_URL = "https://hl7.org.fhir/sid/us-npi";
  public static final String IDENTIFIER_SYSTEM = "urn:ietf:rfc:3986";
  public static final String CONCEPT_MAPPING_EXTENSION = "https://www.lantanagroup.com/fhir/StructureDefinition/mapped-concept";
  public static final String EXTENSION_CRITERIA_REFERENCE = "https://hl7.org/fhir/us/davinci-deqm/StructureDefinition/extension-criteriaReference";
  public static final String MEASURED_VALUES = "https://hl7.org/fhir/uv/saner/CodeSystem/MeasuredValues";
  public static final String ORIGINAL_ENCOUNTER_STATUS = "https://www.lantanagroup.com/fhir/StructureDefinition/nhsn-encounter-original-status";
  public static final String PATIENT_DATA_TAG = "patient-data";
  public static final String MEASURE_REPORT_BUNDLE_PROFILE_URL = "https://www.lantanagroup.com/fhir/StructureDefinition/measure-report-bundle";
  public static final String LINK_VERSION_URL = "https://www.cdc.gov/nhsn/fhir/nhsnlink/StructureDefinition/link-version";
  public static final String MEASURE_VERSION_URL = "https://www.cdc.gov/nhsn/fhir/nhsnlink/StructureDefinition/measure-version";
  public static final String LINK_USER = "link-user";
  public static final String SANER_JOB_TYPE_SYSTEM = "https://thsa1.sanerproject.org:10443/fhir/CodeSystem/saner-job-types";
  public static final Coding EXPUNGE_TASK = new Coding().setCode("expunge-data").setSystem(SANER_JOB_TYPE_SYSTEM).setDisplay("Expunge Data");
  public static final Coding MANUAL_EXPUNGE = new Coding().setCode("manual-expunge").setSystem(SANER_JOB_TYPE_SYSTEM).setDisplay("Manual Expunge");
  public static final Coding REFRESH_PATIENT_LIST = new Coding().setCode("refresh-patient-list").setSystem(SANER_JOB_TYPE_SYSTEM).setDisplay("Refresh Patient List");
  public static final Coding GENERATE_REPORT = new Coding().setCode("generate-report").setSystem(SANER_JOB_TYPE_SYSTEM).setDisplay("Generate Report");
  public static final Coding FILE_UPLOAD = new Coding().setCode("file-upload").setSystem(SANER_JOB_TYPE_SYSTEM).setDisplay("Upload Data File");
  public static final Coding EXTERNAL_FILE_DOWNLOAD = new Coding().setCode("external-file-download").setSystem(SANER_JOB_TYPE_SYSTEM).setDisplay("File Downloaded From Source");
  public static final String DOCUMENT_REFERENCE_VERSION_URL = "https://www.cdc.gov/nhsn/fhir/nhsnlink/StructureDefinition/nhsnlink-report-version";
  public static final Coding SCOOP_DATA = new Coding().setCode("scoop-data").setSystem(SANER_JOB_TYPE_SYSTEM).setDisplay("Scoop Data");
  public static final Coding PATIENT_LIST_PULL = new Coding().setCode("patient-list-pull").setSystem(SANER_JOB_TYPE_SYSTEM).setDisplay("Patient List Pull");
  public static final Coding LOCATION_CREATE_UPDATE = new Coding().setCode("location-create-update").setSystem(SANER_JOB_TYPE_SYSTEM).setDisplay("Location Create/Update");
  public static final Coding TOTALS_CREATE_UPDATE = new Coding().setCode("totals-create-update").setSystem(SANER_JOB_TYPE_SYSTEM).setDisplay("Totals Create/Update");
  public static final String MEASURE_REPORT_PROFILE = "https://hl7.org/fhir/uv/saner/StructureDefinition/PublicHealthMeasureReport";
  public static final Coding CSV_TO_MEASURE_REPORT = new Coding().setCode("csv-to-measure-report").setSystem(SANER_JOB_TYPE_SYSTEM).setDisplay("CSV To MeasureReport");

  public static final Coding REMOTE_ADDRESS = new Coding()
          .setSystem("https://thsa1.sanerproject.org:10443/fhir/CodeSystem/task-input-types")
          .setCode("remote-address")
          .setDisplay("Remote Address");

  public static final Coding NDMS_AGGREGATE_MEASURE_REPORT = new Coding()
          .setCode("ndms-aggregate-measure-report")
          .setDisplay("NDMS Aggregate Measure Report")
          .setSystem(Constants.MAIN_SYSTEM);

  public static final Coding NDMS_CURRENT_AGGREGATE_MEASURE_REPORT = new Coding()
          .setCode("ndms-current-aggregate-measure-report")
          .setDisplay("NDMS Current Aggregate Measure Report")
          .setSystem(Constants.MAIN_SYSTEM);

}
