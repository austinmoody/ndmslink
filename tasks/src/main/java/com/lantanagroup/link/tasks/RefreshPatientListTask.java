package com.lantanagroup.link.tasks;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import ca.uhn.fhir.rest.client.impl.BaseClient;
import ca.uhn.fhir.rest.client.interceptor.AdditionalRequestHeadersInterceptor;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantanagroup.link.Constants;
import com.lantanagroup.link.FhirContextProvider;
import com.lantanagroup.link.Helper;
import com.lantanagroup.link.auth.OAuth2Helper;
import com.lantanagroup.link.config.query.QueryConfig;
import com.lantanagroup.link.helpers.HttpExecutor;
import com.lantanagroup.link.helpers.HttpExecutorResponse;
import com.lantanagroup.link.model.Job;
import com.lantanagroup.link.query.auth.HapiFhirAuthenticationInterceptor;
import com.lantanagroup.link.query.auth.ICustomAuthConfig;
import com.lantanagroup.link.tasks.config.CensusReportingPeriods;
import com.lantanagroup.link.tasks.config.RefreshPatientListConfig;
import org.apache.http.HttpException;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.hl7.fhir.r4.model.ListResource;
import org.hl7.fhir.r4.model.Period;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

public class RefreshPatientListTask {

    private static final Logger logger = LoggerFactory.getLogger(RefreshPatientListTask.class);

    private RefreshPatientListTask() {}

    public static void runRefreshPatientList(RefreshPatientListConfig config, QueryConfig queryConfig, ICustomAuthConfig authConfig) throws Exception {
        HapiFhirAuthenticationInterceptor interceptor = new HapiFhirAuthenticationInterceptor(queryConfig, authConfig);
        execute(config, interceptor);
    }

    public static void runRefreshPatientList(RefreshPatientListConfig config, QueryConfig queryConfig, ApplicationContext applicationContext) throws Exception {
        HapiFhirAuthenticationInterceptor interceptor = new HapiFhirAuthenticationInterceptor(queryConfig, applicationContext);
        execute(config, interceptor);
    }

    private static void execute(RefreshPatientListConfig config, HapiFhirAuthenticationInterceptor interceptor) throws Exception {
        List<RefreshPatientListConfig.PatientList> filteredList = config.getPatientList();

        for (RefreshPatientListConfig.PatientList listResource : filteredList) {
            logger.info("Reading {} List ID {}", listResource.getPatientListLocation(), listResource.getPatientListIdentifier());
            ListResource source = readList(config, listResource.getPatientListIdentifier(), interceptor);
            logger.info("List has {} items", source.getEntry().size());
            ListResource target = transformList(config, source, listResource.getPatientListLocation());
            updateList(config, target);
        }
    }

    private static ListResource readList(RefreshPatientListConfig config, String patientListId, HapiFhirAuthenticationInterceptor interceptor) {

        System.setProperty("ca.uhn.fhir.parser.stax", "com.ctc.wstx.stax.WstxInputFactory");
        FhirContext fhirContext = FhirContext.forR4();

        fhirContext.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);

        AdditionalRequestHeadersInterceptor headersInterceptor = new AdditionalRequestHeadersInterceptor();
        headersInterceptor.addHeaderValue("Accept","application/json");

        IGenericClient client = fhirContext.newRestfulGenericClient(config.getFhirServerBase());
        if (client instanceof BaseClient) {
            ((BaseClient) client).setKeepResponses(true);
        }
        client.registerInterceptor(interceptor);
        client.registerInterceptor(headersInterceptor);

        ListResource r = client.fetchResourceFromUrl(ListResource.class, String.format("List/%s",patientListId));
        if (r != null) {
            return r;
        } else {
            throw new IllegalArgumentException(String.format("Issue getting ListResource for Patient List ID %s", patientListId));
        }
    }

    private static ListResource transformList(RefreshPatientListConfig config, ListResource source, String listLocation) throws URISyntaxException {
        logger.info("Transforming List");
        ListResource target = new ListResource();
        Period period = new Period();
        CensusReportingPeriods reportingPeriod = config.getCensusReportingPeriod();
        if (reportingPeriod == null) {
            reportingPeriod = CensusReportingPeriods.Day;
        }

        if (reportingPeriod.equals(CensusReportingPeriods.Month)) {
            period
                    .setStart(Helper.getStartOfMonth(source.getDate()))
                    .setEnd(Helper.getEndOfMonth(source.getDate(), 0));
        } else if (reportingPeriod.equals(CensusReportingPeriods.Day)) {
            period
                    .setStart(Helper.getStartOfDay(source.getDate()))
                    .setEnd(Helper.getEndOfDay(source.getDate(), 0));
        }

        target.addExtension(Constants.APPLICABLE_PERIOD_EXTENSION_URL, period);
        target.addIdentifier()
                .setSystem(Constants.MAIN_SYSTEM)
                .setValue(listLocation);
        target.setStatus(ListResource.ListStatus.CURRENT);
        target.setMode(ListResource.ListMode.WORKING);
        target.setTitle(String.format("Patient List for %s", listLocation));
        target.setCode(source.getCode());
        target.setDate(source.getDate());
        URI baseUrl = new URI(config.getFhirServerBase());
        for (ListResource.ListEntryComponent sourceEntry : source.getEntry()) {
            target.addEntry(transformListEntry(sourceEntry, baseUrl));
        }
        return target;
    }

    private static ListResource.ListEntryComponent transformListEntry(ListResource.ListEntryComponent source, URI baseUrl)
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

    private static void updateList(RefreshPatientListConfig config, ListResource target) throws Exception {
        String url = config.getApiUrl();
        logger.info("Submitting to {}", url);

        FhirContext fhirContext = FhirContextProvider.getFhirContext();

        HttpPost request = new HttpPost(url);
        if (config.getAuth() != null && config.getAuth().getCredentialMode() != null) {
            String token = OAuth2Helper.getToken(config.getAuth());

            if (token == null) {
                throw new AuthenticationException("Authorization failed");
            }

            if (Boolean.TRUE.equals(OAuth2Helper.validateHeaderJwtToken(token))) {
                request.addHeader(HttpHeaders.AUTHORIZATION, String.format("Bearer %s", token));
            } else {
                throw new JWTVerificationException("Invalid token format");
            }

        }
        request.addHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString());
        request.setEntity(new StringEntity(fhirContext.newJsonParser().encodeResourceToString(target)));

        HttpExecutorResponse response = HttpExecutor.HttpExecutor(request);

        logger.info("HTTP Response Code {}", response.getResponseCode());

        if (response.getResponseCode() != 200) {
            // Didn't get success status from API
            throw new HttpException(String.format("Expecting HTTP Status Code 200 from API, received %s", response.getResponseCode()));
        }
        ObjectMapper mapper = new ObjectMapper();
        Job job = mapper.readValue(response.getResponseBody(), Job.class);

        logger.info("API has started Patient List Load job with ID {}", job.getId());
    }
}
