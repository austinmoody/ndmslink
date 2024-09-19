package com.lantanagroup.link.cli;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Utility {

    private static final Logger logger = LoggerFactory.getLogger(Utility.class);

    public static HttpResponse HttpExecuter(HttpUriRequest request, Logger logger) throws IOException {
        try {
            CloseableHttpClient httpClient = HttpClients.createDefault();
            return httpClient.execute(request, response -> {

                logger.info("Before Get Entity");
                HttpEntity entity = response.getEntity();
                logger.info("After Get Entity");

                if (entity != null) {
                    String body = EntityUtils.toString(entity);
                    if (StringUtils.isNotEmpty(body)) {
                        logger.debug(body);
                    }
                }
                return response;
            });
        } catch (Exception ex) {
            logger.error("HTTP Client Execute issue: {}", ex.getMessage());
            throw ex;
        }
    }

    public static CloseableHttpResponse HttpExecutor(HttpUriRequest request) throws Exception {
        try {
            CloseableHttpClient httpClient = HttpClients.createDefault();
            CloseableHttpResponse httpResponse = httpClient.execute(request);
            httpClient.close();
            return httpResponse;
        } catch (Exception ex) {
            logger.error("HttpExecutor Error: {}", ex.getMessage());
            throw ex;
        }
    }
}
