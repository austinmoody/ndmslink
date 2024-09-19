package com.lantanagroup.link.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

@ShellComponent
public class AustinTester extends BaseShellCommand {

    private static final Logger logger = LoggerFactory.getLogger(AustinTester.class);
    @ShellMethod(key = "austin-tester", value="Testing new Tasks")
    public void execute() {


    }
}
