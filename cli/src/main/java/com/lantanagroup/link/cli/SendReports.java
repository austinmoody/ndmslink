package com.lantanagroup.link.cli;

import com.lantanagroup.link.tasks.SendReportsTask;
import com.lantanagroup.link.tasks.config.SendReportsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

@ShellComponent
public class SendReports extends BaseShellCommand {
    private static final Logger logger = LoggerFactory.getLogger(SendReports.class);

    @ShellMethod(key = "send-reports", value = "Finds reports that have not been sent and sends them.")
    public void execute() {
        try {
            logger.info("SendReports - CLI - Started");
            registerBeans();
            SendReportsConfig config = applicationContext.getBean(SendReportsConfig.class);

            SendReportsTask.executeTask(config);

            logger.info("SendReports - CLI - Completed");
        } catch (Exception ex) {
            logger.error("Issue with SendReports CLI - {}", ex.getMessage());
            System.exit(1);
        }

        System.exit(0);
    }
}
