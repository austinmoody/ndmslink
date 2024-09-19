import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.lantanagroup.link.config.auth.LinkOAuthConfig;
import com.lantanagroup.link.tasks.GenerateReportTask;
import com.lantanagroup.link.tasks.config.GenerateReportConfig;
import com.lantanagroup.link.tasks.helpers.EndDateAdjuster;
import com.lantanagroup.link.tasks.helpers.StartDateAdjuster;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;

public class GenerateReport  implements RequestHandler<Void,String> {

    private static final Logger logger = LoggerFactory.getLogger(GenerateReport.class);
    @Override
    public String handleRequest(Void unused, Context context) {
        String returnValue = "";

        try {
            logger.info("GenerateReport Lambda - Started");

            String authSecretName = System.getenv("API_AUTH_SECRET");
            Region region = Region.of(System.getenv("AWS_REGION"));
            String apiUrl = System.getenv("API_ENDPOINT");
            String genererateReportSecretName = System.getenv("GENERATE_REPORT_SECRET");
            logger.info("Environment Variables Read");

            LinkOAuthConfig authConfig = Utility.GetLinkOAuthConfigFromAwsSecrets(region, authSecretName);
            logger.info("LinkOAuthConfig Created");

            JSONObject generateReportSecretObject = Utility.GetAwsSecretAsJson(region, genererateReportSecretName);

            GenerateReportConfig config = new GenerateReportConfig();
            config.setApiUrl(apiUrl);
            config.setAuth(authConfig);
            config.setBundleIds(Utility.toStringArray(generateReportSecretObject.getJSONArray("bundleIds")));
            config.setStartDate(getStartDateAdjuster(generateReportSecretObject.getJSONObject("start-date")));
            config.setEndDate(getEndDateAdjuster(generateReportSecretObject.getJSONObject("end-date")));

            GenerateReportTask.executeTask(config);

            logger.info("GenerateReport Lambda - Completed");
        } catch (Exception ex) {
            logger.error(ex.getMessage());
            throw new RuntimeException(ex);
        }

        return returnValue;
    }

    private StartDateAdjuster getStartDateAdjuster(JSONObject input) {

        StartDateAdjuster adjuster = new StartDateAdjuster(
                input.getInt("adjust-days"),
                input.getInt("adjust-months"),
                input.getBoolean("date-edge")
        );

        return adjuster;
    }

    private EndDateAdjuster getEndDateAdjuster(JSONObject input) {
        EndDateAdjuster adjuster = new EndDateAdjuster(
                input.getInt("adjust-days"),
                input.getInt("adjust-months"),
                input.getBoolean("date-edge")
        );

        return adjuster;
    }
}
