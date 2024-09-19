package com.lantanagroup.link.cli;

import com.lantanagroup.link.tasks.GenerateReportTask;
import com.lantanagroup.link.tasks.config.GenerateReportConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

@ShellComponent
public class GenerateReportCommand extends BaseShellCommand {
    private static final Logger logger = LoggerFactory.getLogger(GenerateReportCommand.class);
    @ShellMethod(key = "generate-report", value="Testing new Tasks")
    public void execute() {
        try {
            logger.info("GenerateReport - CLI - Started");
            registerBeans();
            GenerateReportConfig config = applicationContext.getBean(GenerateReportConfig.class);

            GenerateReportTask.executeTask(config);

            logger.info("GenerateReport - CLI - Completed");

        } catch (Exception ex) {
            System.exit(1);
        }

        System.exit(0);
    }
}
