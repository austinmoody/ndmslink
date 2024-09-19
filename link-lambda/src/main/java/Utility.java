import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.lantanagroup.link.config.auth.LinkOAuthConfig;
import com.lantanagroup.link.config.query.QueryConfig;
import com.lantanagroup.link.query.auth.EpicAuthConfig;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.regions.Region;

// TODO - do we need this?

import java.io.IOException;

public class Utility {

    public static HttpResponse HttpExecuter(HttpUriRequest request, LambdaLogger logger) throws IOException {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        return httpClient.execute(request, response -> {
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                String body = EntityUtils.toString(entity);
                if (StringUtils.isNotEmpty(body)) {
                    logger.log(body);
                }
            }
            return response;
        });
    }

    public static JSONObject GetAwsSecretAsJson(Region region, String secretName) {

        GetSecretValueResponse getSecretValueResponse;
        try (SecretsManagerClient client = SecretsManagerClient.builder()
                .region(region)
                .httpClient(ApacheHttpClient.create())
                .build()) {

            GetSecretValueRequest getSecretValueRequest = GetSecretValueRequest.builder()
                    .secretId(secretName)
                    .build();

            getSecretValueResponse = client.getSecretValue(getSecretValueRequest);
        }

        String secret = getSecretValueResponse.secretString();

        return new JSONObject(secret);

    }

    public static LinkOAuthConfig GetLinkOAuthConfigFromAwsSecrets(Region region, String secretName) {
        LinkOAuthConfig authConfig = new LinkOAuthConfig();

        JSONObject secretObject = GetAwsSecretAsJson(region, secretName);

        authConfig.setTokenUrl(secretObject.getString("token-url"));
        authConfig.setClientId(secretObject.getString("client-id"));
        authConfig.setClientSecret(secretObject.getString("client-secret"));
        authConfig.setUsername(secretObject.getString("username"));
        authConfig.setPassword(secretObject.getString("password"));
        authConfig.setScope(secretObject.getString("scope"));
        authConfig.setCredentialMode(secretObject.getString("credential-mode"));

        return authConfig;
    }

    public static QueryConfig GetQueryConfigFromAwsSecrets(Region region, String secretName) {
        QueryConfig queryConfig = new QueryConfig();

        JSONObject secretObject = GetAwsSecretAsJson(region, secretName);

        queryConfig.setQueryClass(secretObject.getString("query-class"));
        queryConfig.setAuthClass(secretObject.getString("auth-class"));

        return queryConfig;
    }

    public static EpicAuthConfig GetEpicAuthConfigFromAwsSecrets(Region region, String secretName) {
        EpicAuthConfig authConfig = new EpicAuthConfig();

        JSONObject secretObject = GetAwsSecretAsJson(region, secretName);

        authConfig.setAudience(secretObject.getString("audience"));
        authConfig.setKey(secretObject.getString("key"));
        authConfig.setClientId(secretObject.getString("client-id"));
        authConfig.setTokenUrl(secretObject.getString("token-url"));

        return authConfig;
    }

    public static String[] toStringArray(JSONArray input) {
        String [] returnVal = new String[input.length()];

        for (int i = 0; i < input.length(); i++) {
            returnVal[i] = input.getString(i);
        }

        return returnVal;
    }
}
