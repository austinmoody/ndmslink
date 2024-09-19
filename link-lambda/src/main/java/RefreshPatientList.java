import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.lantanagroup.link.config.auth.LinkOAuthConfig;
import com.lantanagroup.link.config.query.QueryConfig;
import com.lantanagroup.link.query.auth.EpicAuthConfig;
import com.lantanagroup.link.tasks.RefreshPatientListTask;
import com.lantanagroup.link.tasks.config.CensusReportingPeriods;
import com.lantanagroup.link.tasks.config.RefreshPatientListConfig;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;

import java.util.ArrayList;
import java.util.List;

public class RefreshPatientList implements RequestHandler<Void,String> {

    private static final Logger logger = LoggerFactory.getLogger(RefreshPatientList.class);
    @Override
    public String handleRequest(Void unused, Context context) {
        String returnValue = "";

        try {

            logger.info("RefreshPatientList Lambda - Started");

            String apiAuthSecretName = System.getenv("API_AUTH_SECRET"); // dev-thsa-link-api-authentication
            Region region = Region.of(System.getenv("AWS_REGION"));
            String apiUrl = System.getenv("API_ENDPOINT");
            String epicQuerySecretName = System.getenv("PARKLAND_EPIC_QUERY_SECRET"); // dev-thsa-link-parkland-epic-query
            String epicAuthSecretName = System.getenv("PARKLAND_EPIC_AUTH_SECRET"); // dev-thsa-link-parkland-epic-auth
            String refreshPatientListSecret = System.getenv("PARKLAND_REFRESH_PATIENT_SECRET"); // dev-thsa-link-parkland-refresh-patient-list
            logger.info("Environment Variables Read");

            LinkOAuthConfig authConfig = Utility.GetLinkOAuthConfigFromAwsSecrets(region, apiAuthSecretName);
            logger.info("LinkOAuthConfig Created");

            // Read EPIC Query information from Secrets
            QueryConfig queryConfig = Utility.GetQueryConfigFromAwsSecrets(region, epicQuerySecretName);

            // Read EPIC Authentication information from Secrets
            EpicAuthConfig epicAuthConfig = Utility.GetEpicAuthConfigFromAwsSecrets(region, epicAuthSecretName);

            // Read RefreshPatientList configuration from Secrets
            RefreshPatientListConfig config = GetRefreshPatientListconfigFromAwsSecrets(region, refreshPatientListSecret);
            config.setApiUrl(apiUrl);
            config.setAuth(authConfig);

            RefreshPatientListTask.RunRefreshPatientList(config, queryConfig, epicAuthConfig);

            logger.info("RefreshPatientList Lambda - Completed");

        } catch (Exception ex) {
            logger.error(ex.getMessage());
            throw new RuntimeException(ex);
        }

        return returnValue;
    }

    private RefreshPatientListConfig GetRefreshPatientListconfigFromAwsSecrets(Region region, String secretName) {
        RefreshPatientListConfig config = new RefreshPatientListConfig();

        JSONObject secretObject = Utility.GetAwsSecretAsJson(region, secretName);

        config.setFhirServerBase(secretObject.getString("fhir-server-base"));
        config.setCensusReportingPeriod(CensusReportingPeriods.valueOf(secretObject.getString("census-reporting-period")));

        JSONArray patientListsArray = secretObject.getJSONArray("patient-list");

        List<RefreshPatientListConfig.PatientList> patientLists = new ArrayList<>();
        for (Object listObject : patientListsArray) {
            if (listObject instanceof JSONObject) {
                JSONObject patientListObject = (JSONObject) listObject;

                RefreshPatientListConfig.PatientList list = new RefreshPatientListConfig.PatientList();
                list.setPatientListPath(patientListObject.getString("patient-list-path"));

                List<String> censusIdentifiers = new ArrayList<>();
                for (Object censusObject : patientListObject.getJSONArray("census-identifier")) {
                    if (censusObject instanceof String) {
                        censusIdentifiers.add(censusObject.toString());
                    }
                }
                list.setCensusIdentifier(censusIdentifiers);
                patientLists.add(list);
            }
        }

        config.setPatientList(patientLists);

        return config;
    }
}
