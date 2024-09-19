package com.lantanagroup.link.cli;

import com.lantanagroup.link.tasks.ParklandInventoryImportTask;
import com.lantanagroup.link.tasks.config.ParklandInventoryImportConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

@ShellComponent
public class ParklandInventoryImportCommand extends BaseShellCommand {
  private static final Logger logger = LoggerFactory.getLogger(ParklandInventoryImportCommand.class);

  private ParklandInventoryImportConfig config;

  @ShellMethod(key = "parkland-inventory-import", value = "Download an inventory via SFTP and submit it to Link.")
  public void execute(String fileType, @ShellOption(defaultValue="") String fileName) {
    try {
      logger.info("Parkland Inventory Import ({}} Started", fileType);

      registerBeans();
      config = applicationContext.getBean(ParklandInventoryImportConfig.class);
      validate(config);
      logger.info("Configuration Validated");

      ParklandInventoryImportTask.RunParklandInventoryImportTask(config, fileType, fileName);

    } catch (Exception ex) {
      logger.error("Parkland Inventory Import execute issue: {}", ex.getMessage());
      System.exit(1);
    }
    System.exit(0);
  }
}
