package com.lantanagroup.link.cli;

import ca.uhn.fhir.context.FhirContext;
import com.lantanagroup.link.FhirContextProvider;
import com.lantanagroup.link.config.query.QueryConfig;
import com.lantanagroup.link.query.auth.EpicAuth;
import com.lantanagroup.link.query.auth.EpicAuthConfig;
import com.lantanagroup.link.tasks.RefreshPatientListTask;
import com.lantanagroup.link.tasks.config.RefreshPatientListConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

import java.util.List;

@ShellComponent
public class RefreshPatientListCommand extends BaseShellCommand {
  private static final Logger logger = LoggerFactory.getLogger(RefreshPatientListCommand.class);
  private final FhirContext fhirContext = FhirContextProvider.getFhirContext();
  private RefreshPatientListConfig config;
  private QueryConfig queryConfig;

  @Override
  protected List<Class<?>> getBeanClasses() {

    return List.of(
            QueryConfig.class,
            EpicAuth.class,
            EpicAuthConfig.class);
  }

  @ShellMethod(
          key = "refresh-patient-list",
          value = "Read patient lists and update the corresponding census in Link.")
  public void execute() throws Exception {

    try {
      registerBeans();
      config = applicationContext.getBean(RefreshPatientListConfig.class);
      queryConfig = applicationContext.getBean(QueryConfig.class);

      RefreshPatientListTask.RunRefreshPatientList(config, queryConfig, applicationContext);

    } catch (Exception ex) {
      logger.error("RefreshPatientListCommand error: {}", ex.getMessage());
      System.exit(1);
    }

    System.exit(0);

  }

}
