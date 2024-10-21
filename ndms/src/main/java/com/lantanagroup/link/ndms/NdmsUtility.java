package com.lantanagroup.link.ndms;

import com.lantanagroup.link.Constants;
import com.lantanagroup.link.FhirDataProvider;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.*;

import java.util.List;

public class NdmsUtility {
    private ConceptMap trac2esNdmsConceptMap;

    public NdmsUtility() {
        trac2esNdmsConceptMap = null;
    }

    public CodeableConcept getAvailPopulationCodeByTrac2es(String evaluationServiceLocation, String conceptMapLocation, String trac2esCode) {
        return getPopulationCodeByTrac2es(evaluationServiceLocation, conceptMapLocation, trac2esCode, "available");
    }

    public CodeableConcept getOccPopulationCodeByTrac2es(String evaluationServiceLocation, String conceptMapLocation, String trac2esCode) {
        return getPopulationCodeByTrac2es(evaluationServiceLocation, conceptMapLocation, trac2esCode, "occupied");
    }

    public Address getFirstAddressWithGeoLocation(List<Address> addresses) {
        for (Address address : addresses) {
            for (Extension extension : address.getExtension()) {
                if (extension.getUrl().equals(Constants.FHIR_GEOLOCATION_URL)) {
                    return address;
                }
            }
        }

        return null;
    }

    public Extension getGeoLocationAddressExtension(Organization organization) {
        Extension addressExtension = new Extension();
        addressExtension.setUrl("https://hl7.org/fhir/StructureDefinition/Address");

        addressExtension.setValue(
                getFirstAddressWithGeoLocation(organization.getAddress())
        );

        if (!addressExtension.hasValue()) {
            throw new FHIRException(String.format("Report Context Organization (%s) does not contain necessary geo location", organization.getId()));
        }

        return addressExtension;
    }

    public void addOrganizationToMeasureReport(MeasureReport measureReport, Organization organization) {
        // Add Reference extension pointing to Organization
        Extension extension = getReferenceExtension(organization);
        measureReport.addExtension(extension);

        // Add Address w/ GeoLocation as Extension to Organization
        extension = getGeoLocationAddressExtension(organization);
        measureReport.addExtension(extension);

        // Add Reporter w/ just display with name
        Reference reporterReference = new Reference();
        reporterReference.setDisplay(organization.getName());
        measureReport.setReporter(reporterReference);
    }

    public Extension getReferenceExtension(Organization organization) {
        Extension extension = new Extension();
        extension.setUrl("https://hl7.org/fhir/StructureDefinition/Reference");

        Reference reference = new Reference();
        reference.setReference(String.format("%s/%s", organization.getClass().getSimpleName(), organization.getIdElement().getIdPart()));
        reference.setDisplay(organization.getName());

        extension.setValue(reference);

        return extension;
    }

    private CodeableConcept getPopulationCodeByTrac2es(String evaluationServiceLocation, String conceptMapLocation, String trac2esCode, String extensionValue) {
        // Here we are given a TRAC2ES code (which is being used at the Group level)
        // We use that to look up via ConceptMap which Population level Occupied
        // code to use.
        // This function also receives the extension value which
        // should be occupied, available, or total
        // See src/main/resources/fhir/cqf/trac2es-to-ndms.json

        // TODO - need a default "no map" concept.
        CodeableConcept codeableConcept = null;

        // Pull down the configured ConceptMap if necessary
        if ((trac2esNdmsConceptMap == null) || trac2esNdmsConceptMap.isEmpty()) {
            FhirDataProvider evaluationService = new FhirDataProvider(evaluationServiceLocation);
            trac2esNdmsConceptMap = evaluationService.getConceptMapById(conceptMapLocation);
        }

        // Loop groups, find the one where the element w/ extension.url = urn:trac2es:ndms:grouptype and extension.valueCode = passed in extensionValue
        for (ConceptMap.ConceptMapGroupComponent group : trac2esNdmsConceptMap.getGroup()) {
            Extension extension = group.getExtensionByUrl("urn:trac2es:ndms:grouptype");
            if ((extension != null) && extension.getValue().toString().equals(extensionValue) ) {
                // Found the right group, loop elements until we find TRAC2ES code
                for (ConceptMap.SourceElementComponent element : group.getElement()) {
                    if (element.getCode().equals(trac2esCode)) {
                        Coding coding = new Coding();
                        // REFACTOR: Assuming only 1 target per element
                        coding.setCode(element.getTargetFirstRep().getCode());
                        coding.setDisplay(element.getTargetFirstRep().getDisplay());
                        // The System will be the URL from the "target" of this group
                        // which should be the NdmsMeasuredValues
                        // whereas the "source" is Trac2esBedTypes which contains the codes we are using to lookup
                        coding.setSystem(group.getTarget());
                        codeableConcept = new CodeableConcept(coding);
                    }
                }
            }
        }
        return codeableConcept;
    }
}
