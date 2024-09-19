import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.lantanagroup.link.config.auth.LinkOAuthConfig;
import com.lantanagroup.link.tasks.ExpungeDataTask;
import com.lantanagroup.link.tasks.config.ExpungeDataConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;

//aws ecr-public get-login-password --region us-east-1 --profile starhie | docker login --username AWS --password-stdin public.ecr.aws/lambda/java
public class ExpungeData implements RequestHandler<Void,String> {

    private static final Logger logger = LoggerFactory.getLogger(ExpungeDataTask.class);
    @Override
    public String handleRequest(Void unused, Context context) {

        String returnValue = "";

        try {
            logger.info("ExpungeData Lambda - Started");

            String secretName = System.getenv("API_AUTH_SECRET");
            Region region = Region.of(System.getenv("AWS_REGION"));
            String expungeApiUrl = System.getenv("API_ENDPOINT");
            logger.info("Environment Variables Read");

            LinkOAuthConfig authConfig = Utility.GetLinkOAuthConfigFromAwsSecrets(region, secretName);
            logger.info("LinkOAuthConfig Created");

            ExpungeDataConfig config = new ExpungeDataConfig();
            config.setApiUrl(expungeApiUrl);
            config.setAuth(authConfig);
            logger.info("ExpungeDataConfig Created");

            ExpungeDataTask.RunExpungeDataTask(config);

            logger.info("ExpungeData Lambda - Completed");
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        return returnValue;
    }
}
