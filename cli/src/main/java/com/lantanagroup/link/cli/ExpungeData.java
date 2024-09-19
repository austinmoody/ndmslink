package com.lantanagroup.link.cli;

import com.lantanagroup.link.tasks.ExpungeDataTask;
import com.lantanagroup.link.tasks.config.ExpungeDataConfig;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

@ShellComponent
public class ExpungeData extends BaseShellCommand {
    private static final Logger logger = LoggerFactory.getLogger(ExpungeDataConfig.class);

    @ShellMethod(
            key = "expunge-data",
            value = "Call API expunge function to clear data")
    public void execute() {

        try {
            ExpungeDataConfig config = applicationContext.getBean(ExpungeDataConfig.class);
            ExpungeDataTask.RunExpungeDataTask(config);
        } catch (Exception ex) {
            System.exit(1);
        }

        System.exit(0);

    }

    private Boolean ValidConfiguration(ExpungeDataConfig config) {
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
