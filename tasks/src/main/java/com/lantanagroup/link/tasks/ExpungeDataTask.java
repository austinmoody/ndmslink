package com.lantanagroup.link.tasks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantanagroup.link.auth.OAuth2Helper;
import com.lantanagroup.link.helpers.HttpExecutor;
import com.lantanagroup.link.helpers.HttpExecutorResponse;
import com.lantanagroup.link.model.Job;
import com.lantanagroup.link.tasks.config.ExpungeDataConfig;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.HttpDelete;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExpungeDataTask {
    private static final Logger logger = LoggerFactory.getLogger(ExpungeDataTask.class);
    public static void RunExpungeDataTask(ExpungeDataConfig config) throws Exception {
        logger.info("Expunge Data Call - Start");

        if (!ValidConfiguration(config)) {
            logger.error("Issue with expunge-data configuration...");
            throw new Exception("Issue with expunge-data configuration");
        }

        String url = config.getApiUrl();
        logger.info("Calling API Expunge Data at {}", url);

        try {
            String token = OAuth2Helper.getToken(config.getAuth());
            if (token == null) {
                throw new Exception("Authorization failed");
            }
            HttpDelete request = new HttpDelete(url);
            request.addHeader(HttpHeaders.AUTHORIZATION, String.format("Bearer %s", token));

            HttpExecutorResponse response = HttpExecutor.HttpExecutor(request);
            logger.info("HTTP Response Code {}", response.getResponseCode());

            if (response.getResponseCode() != 200) {
                // Didn't get success status from API
                throw new Exception(String.format("Expecting HTTP Status Code 200 from API, received %s", response.getResponseCode()));
            }

            ObjectMapper mapper = new ObjectMapper();
            Job job = mapper.readValue(response.getResponseBody(), Job.class);

            logger.info("API has started Expunge Data Job with ID {}", job.getId());

        } catch (Exception ex) {
            logger.error("Error calling API Expunge Data - {}", ex.getMessage());
            throw ex;
        }

        logger.info("Expunge Data Call - Complete");
    }

    private static Boolean ValidConfiguration(ExpungeDataConfig config) {
        if (StringUtils.isBlank(config.getApiUrl())) {
            logger.error("Parameter expunge-data.api-url parameter is required.");
            return false;
        }

        if (config.getAuth() == null) {
            logger.error("Parameter expunge-data.auth is required.");
            return false;
        }

        if (config.getAuth().getCredentialMode() == null) {
            logger.error("Parameter expunge-data.auth.credential-mode is required.");
            return false;
        }

        if (!config.getAuth().hasCredentialProperties()) {
            logger.error("Some issue with expunge-data.auth credential properties.");
            return false;
        }

        return true;
    }
}
