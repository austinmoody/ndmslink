package com.lantanagroup.link;

import com.lantanagroup.link.config.bundler.BundlerConfig;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FhirBundler {
    protected static final Logger logger = LoggerFactory.getLogger(FhirBundler.class);
    private static final List<String> SUPPLEMENTAL_DATA_EXTENSION_URLS = List.of(
            "https://hl7.org/fhir/us/davinci-deqm/StructureDefinition/extension-supplementalData",
            "https://hl7.org/fhir/5.0/StructureDefinition/extension-MeasureReport.supplementalDataElement.reference");
    private final BundlerConfig config;
    private final EventService eventService;
    private final Organization org;

    public FhirBundler(BundlerConfig config, EventService eventService) {
        this.config = config;
        this.eventService = eventService;
        this.org = this.createOrganization();
    }

    public FhirBundler(BundlerConfig config) {
        this(config, null);
    }

    public Bundle generateBundle(Collection<MeasureReport> aggregateMeasureReports) {
        Bundle bundle = this.createBundle();
        bundle.addEntry().setResource(this.org);

        triggerEvent(EventTypes.BeforeBundling, bundle);

        for (MeasureReport aggregateMeasureReport : aggregateMeasureReports) {
            this.addMeasureReports(bundle, aggregateMeasureReport);
        }

        triggerEvent(EventTypes.AfterBundling, bundle);

        cleanEntries(bundle);
        return bundle;
    }

    private Organization createOrganization() {
        Organization organization = new Organization();
        organization.getMeta().addProfile(Constants.QI_CORE_ORGANIZATION_PROFILE_URL);

        if (!StringUtils.isEmpty(this.config.getOrgNpi())) {
            organization.setId("" + this.config.getOrgNpi().hashCode());
        } else {
            organization.setId(UUID.randomUUID().toString());
        }

        organization.addType()
                .addCoding()
                .setSystem("https://terminology.hl7.org/CodeSystem/organization-type")
                .setCode("prov")
                .setDisplay("Healthcare Provider");

        if (!StringUtils.isEmpty(this.config.getOrgName())) {
            organization.setName(this.config.getOrgName());
        }

        if (!StringUtils.isEmpty(this.config.getOrgNpi())) {
            organization.addIdentifier()
                    .setSystem(Constants.NATIONAL_PROVIDER_IDENTIFIER_SYSTEM_URL)
                    .setValue(this.config.getOrgNpi());
        }

        if (!StringUtils.isEmpty(this.config.getOrgPhone())) {
            organization.addTelecom()
                    .setSystem(ContactPoint.ContactPointSystem.PHONE)
                    .setValue(this.config.getOrgPhone());
        }

        if (!StringUtils.isEmpty(this.config.getOrgEmail())) {
            organization.addTelecom()
                    .setSystem(ContactPoint.ContactPointSystem.EMAIL)
                    .setValue(this.config.getOrgEmail());
        }

        if (this.config.getOrgAddress() != null) {
            organization.addAddress(this.config.getOrgAddress().getFHIRAddress());
        }

        return organization;
    }

    private Bundle createBundle() {
        Bundle bundle = new Bundle();
        bundle.getMeta()
                .addProfile(config.isMHL() ? Constants.MHL_REPORT_BUNDLE_PROFILE_URL : Constants.REPORT_BUNDLE_PROFILE_URL)
                .addTag((config.isMHL() ? Constants.MHL_SYSTEM : Constants.MAIN_SYSTEM), "report", "Report");
        bundle.getIdentifier()
                .setSystem(Constants.IDENTIFIER_SYSTEM)
                .setValue("urn:uuid:" + UUID.randomUUID());
        bundle.setType(config.getBundleType());
        bundle.setTimestamp(new Date());
        return bundle;
    }

    private void triggerEvent(EventTypes eventType, Bundle bundle) {
        if (eventService == null) {
            return;
        }
        try {
            eventService.triggerDataEvent(eventType, bundle, null, null, null);
        } catch (Exception e) {
            logger.error(String.format("Error occurred in %s handler", eventType), e);
        }
    }

    private void setProfile(Resource resource) {
        String profile = null;

        switch (resource.getResourceType()) {
            case Patient:
                profile = Constants.QI_CORE_PATIENT_PROFILE_URL;
                break;
            case Encounter:
                profile = Constants.US_CORE_ENCOUNTER_PROFILE_URL;
                break;
            case MedicationRequest:
                profile = Constants.US_CORE_MEDICATION_REQUEST_PROFILE_URL;
                break;
            case Medication:
                profile = Constants.US_CORE_MEDICATION_PROFILE_URL;
                break;
            case Condition:
                profile = Constants.US_CORE_CONDITION_PROFILE_URL;
                break;
            case Observation:
                profile = Constants.US_CORE_OBSERVATION_PROFILE_URL;
                break;
        }

        if (!StringUtils.isEmpty(profile)) {
            String finalProfile = profile;
            if (!resource.getMeta().getProfile().stream().anyMatch(p -> p.getValue().equals(finalProfile))) {   // Don't duplicate profile if it already exists
                resource.getMeta().addProfile(profile);
            }
        }
    }

    private void addEntry(Bundle bundle, Resource resource, boolean overwrite) {
        String resourceId = getNonLocalId(resource);
        Bundle.BundleEntryComponent entry = bundle.getEntry().stream()
                .filter(_entry -> getNonLocalId(_entry.getResource()).equals(resourceId))
                .findFirst()
                .orElse(null);
        if (entry == null) {
            this.setProfile(resource);
            bundle.addEntry().setResource(resource);
        } else if (overwrite) {
            entry.setResource(resource);
        }
    }

    private void cleanEntries(Bundle bundle) {
        for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
            Resource resource = entry.getResource();
            String resourceId = getNonLocalId(resource);
            resource.setId(resourceId);

            // Only allow meta.profile
            if (resource.getMeta() != null) {
                Meta cleanedMeta = new Meta();
                cleanedMeta.setProfile(resource.getMeta().getProfile());
                resource.setMeta(cleanedMeta);
            }

            if (resource instanceof DomainResource) {
                ((DomainResource) resource).setText(null);
            }
            entry.setFullUrl(String.format("https://lantanagroup.com/fhir/" +
                    (config.isMHL() ? "nih-measures" : "nhsn-measures") + "/%s", resourceId));
            if (config.getBundleType() == Bundle.BundleType.TRANSACTION
                    || config.getBundleType() == Bundle.BundleType.BATCH) {
                entry.getRequest()
                        .setMethod(Bundle.HTTPVerb.PUT)
                        .setUrl(resourceId);
            }
        }
    }

    private void addMeasureReports(Bundle bundle, MeasureReport aggregateMeasureReport) {
        logger.debug("Adding measure reports: {}", aggregateMeasureReport.getMeasure());

        this.addAggregateMeasureReport(bundle, aggregateMeasureReport);

    }

    private void addAggregateMeasureReport(Bundle bundle, MeasureReport aggregateMeasureReport) {
        logger.debug("Adding aggregate measure report: {}", aggregateMeasureReport.getId());

        // Set the reporter to the facility/org
        aggregateMeasureReport.setReporter(new Reference().setReference("Organization/" + this.org.getIdElement().getIdPart()));

        bundle.addEntry().setResource(aggregateMeasureReport);
    }

    private Map<IIdType, List<Reference>> getLineLevelResources(MeasureReport individualMeasureReport) {
        Stream<Reference> evaluatedResources = individualMeasureReport.getEvaluatedResource().stream();
        Stream<Reference> supplementalDataReferences =
                individualMeasureReport.getExtension().stream()
                        .filter(extension -> SUPPLEMENTAL_DATA_EXTENSION_URLS.contains(extension.getUrl()))
                        .map(Extension::getValue)
                        .filter(value -> value instanceof Reference)
                        .map(value -> (Reference) value);
        return Stream.concat(supplementalDataReferences, evaluatedResources)
                .filter(reference -> reference.hasExtension(Constants.EXTENSION_CRITERIA_REFERENCE))
                .collect(Collectors.groupingBy(Reference::getReferenceElement, LinkedHashMap::new, Collectors.toList()));
    }

    private String getNonLocalId(IBaseResource resource) {
        return String.format("%s/%s", resource.fhirType(), getIdPart(resource));
    }

    private String getIdPart(IBaseResource resource) {
        return resource.getIdElement().getIdPart().replaceAll("^#", "");
    }
}
