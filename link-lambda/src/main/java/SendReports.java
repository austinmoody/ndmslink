import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.lantanagroup.link.config.auth.LinkOAuthConfig;
import com.lantanagroup.link.tasks.SendReportsTask;
import com.lantanagroup.link.tasks.config.SendReportsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;

public class SendReports implements RequestHandler<Void,String> {

    private static final Logger logger = LoggerFactory.getLogger(SendReports.class);
    @Override
    public String handleRequest(Void unused, Context context) {
        String returnValue = "";

        try {
            logger.info("SendReports Lambda - Started");

            String authSecretName = System.getenv("API_AUTH_SECRET");
            Region region = Region.of(System.getenv("AWS_REGION"));
            String searchReportsUrl = System.getenv("SEARCH_REPORTS_URL");
            String sendReportUrl = System.getenv("SEND_REPORT_URL");
            logger.info("Environment Variables Read");

            LinkOAuthConfig authConfig = Utility.GetLinkOAuthConfigFromAwsSecrets(region, authSecretName);
            logger.info("LinkOAuthConfig Created");

            SendReportsConfig config = new SendReportsConfig();
            config.setAuth(authConfig);
            config.setSendReportUrl(sendReportUrl);
            config.setSearchReportsUrl(searchReportsUrl);

            logger.info("API Endpoint to search for reports: {}", config.getSearchReportsUrl());
            logger.info("API Endpoint template to send reports: {}", config.getSendReportUrl());

            logger.info("Calling SendReportsTask");
            SendReportsTask.executeTask(config);

            logger.info("SendReports Lambda - Completed");

        } catch (Exception ex) {
            logger.error(ex.getMessage());
            throw new RuntimeException(ex);
        }
        return returnValue;
    }
}
