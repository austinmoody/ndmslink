package com.lantanagroup.link.cli.helpers;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;

import java.io.IOException;

public class HttpExecutor {
    public static HttpExecutorResponse HttpExecutor(HttpUriRequest request, Logger logger) throws IOException {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpExecutorResponse httpExecutorResponse = new HttpExecutorResponse();
        httpClient.execute(request, response -> {
            httpExecutorResponse.setResponseCode(response.getStatusLine().getStatusCode());
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                String body = EntityUtils.toString(entity);
                if (StringUtils.isNotEmpty(body)) {
                    //logger.debug(body);
                    httpExecutorResponse.setResponseBody(body);
                }
            }
            return response;
        });

        return httpExecutorResponse;
    }
}
