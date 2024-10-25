package com.lantanagroup.link;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.*;
import com.lantanagroup.link.config.datagovernance.RetainResourceConfig;
import org.hl7.fhir.instance.model.api.IAnyResource;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.r4.model.MeasureReport;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.CacheControlDirective;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.api.SummaryEnum;
import ca.uhn.fhir.rest.client.apache.GZipContentInterceptor;
import ca.uhn.fhir.rest.client.interceptor.BasicAuthInterceptor;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.lantanagroup.link.config.api.ApiDataStoreConfig;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;



public class FhirDataProvider {
  private static final Logger logger = LoggerFactory.getLogger(FhirDataProvider.class);
  private static final String REPORT_DOC_REF_SYSTEM = "urn:ietf:rfc:3986";
  protected FhirContext ctx = FhirContextProvider.getFhirContext();

  @Getter
  private final IGenericClient client;

  public FhirDataProvider(IGenericClient client) {
    this.client = client;
  }

  public FhirDataProvider(ApiDataStoreConfig config) {

    if (StringUtils.isNotEmpty(config.getSocketTimeout())) {
      this.ctx.getRestfulClientFactory().setSocketTimeout(Integer.parseInt(config.getSocketTimeout()));
    }

    if (StringUtils.isNotEmpty(config.getConnectionTimeout())) {
      ctx.getRestfulClientFactory().setConnectTimeout(Integer.parseInt(config.getConnectionTimeout()));
    }

    if (StringUtils.isNotEmpty(config.getConnectionRequestTimeout())) {
      ctx.getRestfulClientFactory().setConnectionRequestTimeout(Integer.parseInt(config.getConnectionRequestTimeout()));
    }

    IGenericClient newClient = this.ctx.newRestfulGenericClient(config.getBaseUrl());

    if (StringUtils.isNotEmpty(config.getUsername()) && StringUtils.isNotEmpty(config.getPassword())) {
      BasicAuthInterceptor authInterceptor = new BasicAuthInterceptor(config.getUsername(), config.getPassword());
      newClient.registerInterceptor(authInterceptor);
    }
    this.client = newClient;
  }

  public FhirDataProvider(String fhirBase) {
    this.client = this.ctx.newRestfulGenericClient(fhirBase);
    this.client.registerInterceptor(new GZipContentInterceptor());
  }

  public Resource createResource(IBaseResource resource) {

    MethodOutcome outcome = this.client
            .create()
            .resource(resource)
            .execute();


    if (Boolean.TRUE.equals(!outcome.getCreated()) || outcome.getResource() == null) {
      logger.error("Failed to store/create FHIR resource");
    } else {
      logger.debug("Stored FHIR resource with new {}", outcome.getId());
    }

    return (Resource) outcome.getResource();
  }

  public MethodOutcome updateResource(IBaseResource resource) {
    int initialVersion = resource.getMeta().getVersionId() != null ? Integer.parseInt(resource.getMeta().getVersionId()) : 0;

    // Make sure the ID is not version-specific
    if (resource.getIdElement() != null && resource.getIdElement().getIdPart() != null) {
      resource.setId(resource.getIdElement().getIdPart());
    }

    MethodOutcome outcome = this.client
            .update()
            .resource(resource)
            .execute();

    Resource domainResource = (Resource) outcome.getResource();
    int updatedVersion = Integer.parseInt(outcome.getId().getVersionIdPart());
    if (updatedVersion > initialVersion) {
      logger.debug("Update is successful for {}/{}", domainResource.getResourceType(), domainResource.getIdElement().getIdPart());
    } else {
      logger.info("Nothing changed in resource {}/{}", domainResource.getResourceType(), domainResource.getIdElement().getIdPart());
    }

    return outcome;
  }

  public DocumentReference findDocRefForReport(String reportId) {
    Bundle bundle = this.client
            .search()
            .forResource(DocumentReference.class)
            .where(DocumentReference.IDENTIFIER.exactly().systemAndValues(REPORT_DOC_REF_SYSTEM, reportId))
            .returnBundle(Bundle.class)
            .cacheControl(new CacheControlDirective().setNoCache(true))
            .execute();

    if (bundle.getEntry().size() != 1) {
      return null;
    }

    return (DocumentReference) bundle.getEntryFirstRep().getResource();
  }

  public Bundle findListByIdentifierAndDate(Identifier identifier, String start, String end) {
    return this.client
            .search()
            .forResource(ListResource.class)
            .where(
                    ListResource.IDENTIFIER.exactly().systemAndValues(
                            identifier.getSystem(),
                            identifier.getValue()
                    )
            )
            .and(new DateClientParam("applicable-period-start").exactly().second(start))
            .and(new DateClientParam("applicable-period-end").exactly().second(end))
            .returnBundle(Bundle.class)
            .cacheControl(new CacheControlDirective().setNoCache(true))
            .execute();

  }

  public Bundle findListByIdentifierAndDate(String system, String value, String start, String end) {
    return this.client
            .search()
            .forResource(ListResource.class)
            .where(ListResource.IDENTIFIER.exactly().systemAndValues(system, value))
            .and(new DateClientParam("applicable-period-start").exactly().second(start))
            .and(new DateClientParam("applicable-period-end").exactly().second(end))
            .returnBundle(Bundle.class)
            .cacheControl(new CacheControlDirective().setNoCache(true))
            .execute();
  }

  public Measure getMeasureById(String measureId) {
    return this.client.read().resource(Measure.class).withId(measureId).execute();
  }

  public CodeSystem getCodeSystemById(String codeSystemId) {
    return this.client.read().resource(CodeSystem.class).withId(codeSystemId).execute();
  }

  public ConceptMap getConceptMapById(String conceptMapId) {
    return this.client.read().resource(ConceptMap.class).withId(conceptMapId).execute();
  }

  public Task getTaskById(String taskId) {
    return this.client.read().resource(Task.class).withId(taskId).execute();
  }

  public Location getLocationById(String locationId) {
    return this.client.read().resource(Location.class).withId(locationId).execute();
  }

  public MeasureReport getMeasureReportById(String reportId) {

    return this.client
            .read()
            .resource(MeasureReport.class)
            .withId(reportId)
            .execute();
  }

  public Bundle getBundleById(String bundleId) {

    return this.client
            .read()
            .resource(Bundle.class)
            .withId(bundleId)
            .execute();
  }

  public Bundle getMeasureReportsByIds(List<String> reportIds) {
    return this.client
            .search()
            .forResource(MeasureReport.class)
            .where(IAnyResource.RES_ID.exactly().codes(reportIds))
            .returnBundle(Bundle.class)
            .cacheControl(new CacheControlDirective().setNoCache(true))
            .execute();
  }

  public Bundle transaction(Bundle txBundle) {
    logger.trace("Executing transaction on {}", client.getServerBase());

    return this.client
            .transaction()
            .withBundle(txBundle)
            .execute();
  }

  public Measure findMeasureByIdentifier(Identifier identifier) {
    Bundle measureBundle = this.client.search()
            .forResource("Measure")
            .where(Measure.IDENTIFIER.exactly().systemAndIdentifier(identifier.getSystem(), identifier.getValue()))
            .returnBundle(Bundle.class)
            .cacheControl(new CacheControlDirective().setNoCache(true))
            .execute();

    if (measureBundle.getEntry().size() != 1) {
      return null;
    }

    return (Measure) measureBundle.getEntryFirstRep().getResource();
  }

  public void audit(Task jobTask, DecodedJWT jwt, FhirHelper.AuditEventTypes type, String outcomeDescription) {
    AuditEvent auditEvent = FhirHelper.createAuditEvent(jobTask, jwt, type, outcomeDescription);
    this.createResource(auditEvent);
  }

  /**
   * Gets a resource by type and ID only including the id property to check if the resource exists
   * @param resourceType The type of resource we are trying to get (List, Patient, etc...)
   * @param resourceId The identifier of the resource we are trying to get
   * @return The gotten Resource
   */
  public IBaseResource tryGetResource(String resourceType, String resourceId) {
    return this.client
            .read()
            .resource(resourceType)
            .withId(resourceId)
            .elementsSubset("id")
            .cacheControl(new CacheControlDirective().setNoCache(true))
            .execute();
  }

  /**
   * Gets a complete resource by retrieving it based on type and id
   * @param resourceType The type of resource we are trying to get (List, Patient, etc...)
   * @param resourceId The identifier of the resource we are trying to get
   * @return The gotten Resource
   */
  public IBaseResource getResourceByTypeAndId(String resourceType, String resourceId) {
    return this.client
            .read()
            .resource(resourceType)
            .withId(resourceId)
            .cacheControl(new CacheControlDirective().setNoCache(true))
            .execute();
  }

  public MeasureReport getMeasureReport(String measureId, Parameters parameters) {

    //Uncomment to get payload sent to get MeasureReport
    /*
    FhirContext ctx = FhirContext.forR4();
    IParser parser = ctx.newJsonParser();
    String myRequestBody = parser.encodeResourceToString(parameters);
     */

    return client.operation()
            .onInstance(new IdType("Measure", measureId))
            .named("$evaluate-measure")
            .withParameters(parameters)
            .returnResourceType(MeasureReport.class)
            .cacheControl(new CacheControlDirective().setNoCache(true))
            .execute();
  }

  public Bundle searchPractitioner(String tagSystem, String tagValue) {
    return this.client
            .search()
            .forResource(Practitioner.class)
            .withTag(tagSystem, tagValue)
            .returnBundle(Bundle.class)
            .cacheControl(new CacheControlDirective().setNoCache(true))
            .execute();
  }

  public MethodOutcome createOutcome(IBaseResource resource) {
    return this.client
            .create()
            .resource(resource)
            .prettyPrint()
            .encodedJson()
            .execute();
  }

  public Bundle fetchResourceFromUrl(String url) {
    return this.client.fetchResourceFromUrl(Bundle.class, url);
  }

  public String bundleToXml(Bundle bundle) {
    return client.getFhirContext().newXmlParser().encodeResourceToString(bundle);
  }

  public String bundleToJson(Bundle bundle) {
    return client.getFhirContext().newJsonParser().encodeResourceToString(bundle);
  }

  public Bundle searchReportDefinition(String system, String value) {
    return client.search()
            .forResource(Bundle.class)
            .withTag(Constants.MainSystem, Constants.ReportDefinitionTag)
            .where(Bundle.IDENTIFIER.exactly().systemAndCode(system, value))
            .returnBundle(Bundle.class)
            .cacheControl(new CacheControlDirective().setNoCache(true))
            .execute();
  }

  public Bundle searchPractitioner(String practitionerId) {
    return client
            .search()
            .forResource(Practitioner.class)
            .withTag(Constants.MainSystem, Constants.LinkUserTag)
            .where(Practitioner.IDENTIFIER.exactly().systemAndValues(Constants.MainSystem, practitionerId))
            .returnBundle(Bundle.class)
            .cacheControl(new CacheControlDirective().setNoCache(true))
            .execute();
  }

  public Bundle searchBundleByTag(String system, String value) {
    return client
            .search()
            .forResource(Bundle.class)
            .withTag(system, value)
            .returnBundle(Bundle.class)
            .execute();
  }

  public IBaseResource retrieveFromServer(String resourceType, String resourceId) {
    return client
            .read()
            .resource(resourceType)
            .withId(resourceId)
            .cacheControl(new CacheControlDirective().setNoCache(true))
            .execute();
  }

  public Bundle getResourcesSummaryByCountLastUpdatedExclude(String resourceType,
                                                             Integer count,
                                                             Date lastUpdatedBefore,
                                                             List<RetainResourceConfig> retainResources) {
    IQuery<IBaseBundle> query = getBaseSummaryCountQuery(resourceType, count);
    addLastUpdatedToQuery(query, lastUpdatedBefore);
    addResourceExclusionsToQuery(query, resourceType, retainResources);
    return query.returnBundle(Bundle.class).execute();
  }

  private IQuery<IBaseBundle> getBaseSummaryCountQuery(String resourceType, Integer count) {
    return client
            .search()
            .forResource(resourceType)
            .count(count)
            .summaryMode(SummaryEnum.TRUE);
  }

  private void addLastUpdatedToQuery(IQuery<IBaseBundle> query, Date lastUpdatedBefore) {
    ICriterion<DateClientParam> lastUpdated = new DateClientParam("_lastUpdated").beforeOrEquals().second(lastUpdatedBefore);
    query.where(lastUpdated);
  }

  private void addResourceExclusionsToQuery(IQuery<IBaseBundle> query,
                                            String resourceType,
                                            List<RetainResourceConfig> retainResources) {
    String excludeIds = retainResources.stream().filter(
            rrc -> rrc.getResourceType().equals(resourceType)
    ).map(RetainResourceConfig::getResourceId)
            .collect(Collectors.joining(","));

    if (!excludeIds.isEmpty()) {
      query.where(new StringClientParam("identifier:not").matches().values(excludeIds));
    }
  }

  public Bundle getResourcesSummaryByCountTagLastUpdatedExclude(String resourceType,
                                                                Integer count,
                                                                String tagSystem,
                                                                String tagCode,
                                                                Date lastUpdatedBefore,
                                                                List<RetainResourceConfig> retainResources) {
    IQuery<IBaseBundle> query = getBaseSummaryCountQuery(resourceType, count);
    query.withTag(tagSystem, tagCode);
    ICriterion<DateClientParam> lastUpdated = new DateClientParam("_lastUpdated").beforeOrEquals().second(lastUpdatedBefore);
    query.where(lastUpdated);
    addResourceExclusionsToQuery(query, resourceType, retainResources);
    return query.returnBundle(Bundle.class).execute();
  }

  public Bundle getAllResourcesByType(Class<? extends IBaseResource> classType) {
    return client
            .search()
            .forResource(classType)
            .returnBundle(Bundle.class)
            .execute();
  }

  public void deleteResource(String resourceType, String id, boolean permanent) {

    String url = String.format("%s?_id=%s%s", resourceType, id, (permanent?"&_expunge=true":""));

    client.delete().resourceConditionalByUrl(url).execute();

  }
}

