package com.lantanagroup.link.ndms.publisher;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.apache.GZipContentInterceptor;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.BasicAuthInterceptor;
import com.lantanagroup.link.FhirContextProvider;
import com.lantanagroup.link.FhirDataProvider;
import com.lantanagroup.link.IMeasureReportPublisher;
import com.lantanagroup.link.config.publisher.FhirPublisherAuthType;
import com.lantanagroup.link.config.publisher.FhirPublisherConfig;
import com.lantanagroup.link.config.publisher.PublisherOutcome;
import org.hl7.fhir.r4.model.MeasureReport;
import org.hl7.fhir.r4.model.Meta;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FhirPublisher implements IMeasureReportPublisher<FhirPublisherConfig> {
    private final FhirContext ctx = FhirContextProvider.getFhirContext();
    @Override
    public PublisherOutcome publish(FhirPublisherConfig config, MeasureReport report) {

        PublisherOutcome outcome = new PublisherOutcome();

        try {
            IGenericClient client = ctx.newRestfulGenericClient(config.getUrl());
            client.registerInterceptor(new GZipContentInterceptor());

            if (config.getAuthType().equals(FhirPublisherAuthType.BASIC)) {
                BasicAuthInterceptor authInterceptor = new BasicAuthInterceptor(
                        config.getAuthOptions().get("username"),
                        config.getAuthOptions().get("password")
                );
                client.registerInterceptor(authInterceptor);
            }

            FhirDataProvider outboundFhir = new FhirDataProvider(client);

            // Blank out version of report we are sending to force update on
            // server that we are publishing to.
            report.setMeta(new Meta().setVersionId(null));

            MethodOutcome updateOutcome = outboundFhir.updateResource(report);

            List<String> locations = updateOutcome.getResponseHeaders().get("content-location");

            if (locations != null && locations.size() == 1) {
                outcome.setPublishedLocation(locations.get(0));
            }

            outcome.setSuccess(true);
        } catch (Exception ex) {
            outcome.setMessage(ex.getMessage());
            outcome.setSuccess(false);
        }

        return outcome;
    }
}
