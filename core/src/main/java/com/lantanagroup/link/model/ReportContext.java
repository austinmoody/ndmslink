package com.lantanagroup.link.model;

import com.lantanagroup.link.Constants;
import com.lantanagroup.link.FhirDataProvider;
import com.lantanagroup.link.auth.LinkCredentials;
import lombok.Getter;
import lombok.Setter;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.*;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ReportContext {
  private FhirDataProvider fhirProvider;
  private HttpServletRequest request;
  private LinkCredentials user;
  private String masterIdentifierValue;
  private List<ListResource> patientCensusLists = new ArrayList<>();
  private List<PatientOfInterestModel> patientsOfInterest = new ArrayList<>();
  private List<MeasureContext> measureContexts = new ArrayList<>();
  private Location reportLocation;

  public ReportContext(FhirDataProvider fhirProvider) {
    this.fhirProvider = fhirProvider;
  }


  @Getter
  @Setter
  public static class MeasureContext {
    private String bundleId;
    private Bundle reportDefBundle;
    private Measure measure;
    private String reportId;
    private List<PatientOfInterestModel> patientsOfInterest = new ArrayList<>();
    private List<MeasureReport> patientReports = new ArrayList<>();
    private MeasureReport measureReport;
  }
}
