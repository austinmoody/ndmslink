package com.lantanagroup.link.tasks;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantanagroup.link.auth.OAuth2Helper;
import com.lantanagroup.link.helpers.HttpExecutor;
import com.lantanagroup.link.helpers.HttpExecutorResponse;
import com.lantanagroup.link.model.Job;
import com.lantanagroup.link.model.Report;
import com.lantanagroup.link.model.ReportBundle;
import com.lantanagroup.link.tasks.config.SendReportsConfig;
import org.apache.commons.text.StringSubstitutor;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class SendReportsTask {
    private static final Logger logger = LoggerFactory.getLogger(SendReportsTask.class);

    public static void executeTask(SendReportsConfig config) throws Exception {
        logger.info("SendReports - executeTask - Started");

        try {

            logger.info("Searching for un-sent reports at {}", config.getSearchReportsUrl());

            URIBuilder searchReportUrlBuilder = new URIBuilder(config.getSearchReportsUrl());
            searchReportUrlBuilder.addParameter("submitted", "false");

            HttpGet searchRequest = new HttpGet(searchReportUrlBuilder.build());

            String token = OAuth2Helper.getToken(config.getAuth());
            if (token == null) {
                String errorMessage = "Authentication failed. Please contact the system administrator.";
                logger.error(errorMessage);
                throw new Exception(errorMessage);
            }
            if (OAuth2Helper.validateHeaderJwtToken(token)) {
                searchRequest.addHeader(HttpHeaders.AUTHORIZATION, String.format("Bearer %s", token));
            } else {
                throw new JWTVerificationException("Invalid token format");
            }

            searchRequest.addHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString());

            HttpExecutorResponse searchResponse = HttpExecutor.HttpExecutor(searchRequest);
            logger.info("HTTP Response Code {}", searchResponse.getResponseCode());

            if (searchResponse.getResponseCode() != 200) {
                // Didn't get success status from API
                throw new Exception(String.format("Expecting HTTP Status Code 200 from API, received %s", searchResponse.getResponseCode()));
            }

            ObjectMapper mapper = new ObjectMapper();
            ReportBundle reports = mapper.readValue(searchResponse.getResponseBody(), ReportBundle.class);

            logger.info("There are {} reports to send", reports.getTotalSize());

            for (Report report : reports.getList()) {
                logger.info("Preparing to send report '{}'", report.getId());

                String sendUrl = getSendUrl(config.getSendReportUrl(), report.getId());
                logger.info("Sending report to API via {}", sendUrl);

                HttpPost sendRequest = new HttpPost(sendUrl);
                sendRequest.addHeader(HttpHeaders.AUTHORIZATION, String.format("Bearer %s", token));

                HttpExecutorResponse sendResponse = HttpExecutor.HttpExecutor(sendRequest);
                logger.info("HTTP Response Code {}", sendResponse.getResponseCode());

                if (sendResponse.getResponseCode() != 200) {
                    // Didn't get success status from API
                    throw new Exception(String.format("Expecting HTTP Status Code 200 from API, received %s", sendResponse.getResponseCode()));
                }
                Job job = mapper.readValue(sendResponse.getResponseBody(), Job.class);

                logger.info("Report '{}' has been sent.  Send Report job started with id '{}'", report.getId(), job.getId());
            }


        } catch (Exception ex) {
            logger.error("Issue with SendReport task - {}", ex.getMessage());
            throw ex;
        }

        logger.info("SendReports - executeTask - Completed");
    }

    private static String getSendUrl(String templateUrl, String reportId) {
        // Build URL using sendReportUrl and id from report
        Map<String, Object> urlReplaceParams = new HashMap<>();
        urlReplaceParams.put("reportid", reportId);
        return StringSubstitutor.replace(templateUrl, urlReplaceParams, "${", "}");
    }
}
