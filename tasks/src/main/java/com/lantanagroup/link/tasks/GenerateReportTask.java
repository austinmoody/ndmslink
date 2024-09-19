package com.lantanagroup.link.tasks;

import ca.uhn.fhir.context.ConfigurationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantanagroup.link.Helper;
import com.lantanagroup.link.auth.OAuth2Helper;
import com.lantanagroup.link.helpers.HttpExecutor;
import com.lantanagroup.link.helpers.HttpExecutorResponse;
import com.lantanagroup.link.model.GenerateRequest;
import com.lantanagroup.link.model.Job;
import com.lantanagroup.link.tasks.config.GenerateReportConfig;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GenerateReportTask {
    private static final Logger logger = LoggerFactory.getLogger(GenerateReportTask.class);

    public static void executeTask(GenerateReportConfig config) throws Exception {
        try {

            logger.info("GenerateReportTask executeTask - Started");

            HttpPost request = new HttpPost(config.getApiUrl());

            String token = OAuth2Helper.getToken(config.getAuth());
            if (token == null) {
                String errorMessage = "Authentication failed. Please contact the system administrator.";
                logger.error(errorMessage);
                throw new Exception(errorMessage);
            }
            if (OAuth2Helper.validateHeaderJwtToken(token)) {
                request.addHeader(org.apache.http.HttpHeaders.AUTHORIZATION, String.format("Bearer %s", token));
            } else {
                throw new JWTVerificationException("Invalid token format");
            }

            request.addHeader(org.apache.http.HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString());

            String startDateFormatted = Helper.getFhirDate(config.getStartDate().Date());
            String endDateFormatted = Helper.getFhirDate(config.getEndDate().Date());

            GenerateRequest generateRequest = new GenerateRequest();
            generateRequest.setPeriodEnd(endDateFormatted);
            generateRequest.setPeriodStart(startDateFormatted);
            generateRequest.setRegenerate(true);
            generateRequest.setBundleIds(config.getBundleIds());

            ObjectMapper objectMapper = new ObjectMapper();
            String jsonPayload = objectMapper.writeValueAsString(generateRequest);
            logger.info("Calling API Generate Report with: {}", jsonPayload);

            org.apache.http.HttpEntity generateRequestEntity = new StringEntity(jsonPayload);
            request.setEntity(generateRequestEntity);
            HttpExecutorResponse response = HttpExecutor.HttpExecutor(request);

            logger.info("HTTP Response Code {}", response.getResponseCode());

            if (response.getResponseCode() != 200) {
                // Didn't get success status from API
                throw new Exception(String.format("Expecting HTTP Status Code 200 from API, received %s", response.getResponseCode()));
            }
            ObjectMapper mapper = new ObjectMapper();
            Job job = mapper.readValue(response.getResponseBody(), Job.class);
            logger.info("API has started Generate Report Job with ID {}", job.getId());

            logger.info("GenerateReportTask executeTask - Completed");

        } catch (Exception ex) {
            logger.error("Error with GenerateReportTask - {}", ex.getMessage());
            throw ex;
        }
    }

    private void ValidateConfiguration(GenerateReportConfig config) throws ConfigurationException {
        if (StringUtils.isBlank(config.getApiUrl())) {
            String errorMessage = "API URL not specified in Generate Report Configuration";
            throw new ConfigurationException(errorMessage);
        }

        // Check Start / End Date Stuff
        if (config.getEndDate() == null){
            String errorMessage = "End Date Adjuster not configured";
            throw new ConfigurationException(errorMessage);
        }
        if (config.getStartDate() == null) {
            String errorMessage = "Start Date Adjuster not configured";
            throw new ConfigurationException(errorMessage);
        }

        // Auth Checks
        if (config.getAuth() == null) {
            String errorMessage = "API authorization is required.";
            throw new ConfigurationException(errorMessage);
        }
        if (StringUtils.isBlank(config.getAuth().getTokenUrl())) {
            String errorMessage = "The API Authorization - Token URL is required.";
            throw new ConfigurationException(errorMessage);
        }
        if (StringUtils.isBlank(config.getAuth().getUsername())) {
            String errorMessage = "The API Authorization - Username is required.";
            throw new ConfigurationException(errorMessage);
        }
        if (StringUtils.isBlank(config.getAuth().getPassword())) {
            String errorMessage = "The API Authorization - Password is required.";
            throw new ConfigurationException(errorMessage);
        }
        if (StringUtils.isBlank(config.getAuth().getScope())) {
            String errorMessage = "The API Authorization - Scope is required.";
            throw new ConfigurationException(errorMessage);
        }
        if (StringUtils.isBlank(config.getAuth().getCredentialMode())) {
            String errorMessage = "The API Authorization is invalid";
            throw new ConfigurationException(errorMessage);
        }
    }
}
