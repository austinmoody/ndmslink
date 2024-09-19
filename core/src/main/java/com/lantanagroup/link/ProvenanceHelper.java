package com.lantanagroup.link;

import com.lantanagroup.link.auth.LinkCredentials;
import org.hl7.fhir.r4.model.*;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class ProvenanceHelper {

    public static Provenance getNewProvenance(LinkCredentials user, List<Resource> targets) {
        Provenance provenance = new Provenance();

        provenance.setId(UUID.randomUUID().toString());
        provenance.setRecorded(new Date());

        for (Resource resource : targets) {
            Reference reference = new Reference();
            reference.setReference(String.format("%s/%s", resource.getResourceType().name(), resource.getIdElement().getIdPart()));
            provenance.addTarget(reference);
        }

        Provenance.ProvenanceAgentComponent agent = new Provenance.ProvenanceAgentComponent();
        Reference who = new Reference();
        who.setReference(String.format("Practitioner/%s", user.getPractitioner().getIdentifier().get(0).getValue()));
        agent.setWho(who);
        provenance.setAgent(Collections.singletonList(agent));

        return provenance;
    }
    public static Provenance getNewFileDownloadProvenance(LinkCredentials user, List<Resource> targets, Coding activity, String source, String fileType) {
        Provenance provenance = getNewProvenance(user, targets);

        CodeableConcept activityConcept = new CodeableConcept();
        activityConcept.addCoding(activity);
        provenance.setActivity(activityConcept);

        provenance.getMeta().addTag(Constants.MainSystem,source,"File Download Source");
        provenance.getMeta().addTag(Constants.MainSystem, fileType, "Type Of File Downloaded");
        provenance.getMeta().addTag(activity);

        return provenance;
    }
}
