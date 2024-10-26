package com.lantanagroup.link.ndms;

/*
This is just the start of a test of the EventService to see if it is something we want to
incorporate.
 */

import com.lantanagroup.link.IReportGenerationEvent;
import com.lantanagroup.link.model.ReportContext;
import com.lantanagroup.link.model.ReportCriteria;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NdmsEvent implements IReportGenerationEvent {
    private static final Logger logger = LoggerFactory.getLogger(NdmsEvent.class);

    @Override
    public void execute(ReportCriteria reportCriteria, ReportContext context) {
        logger.info("Executing NdmsEvent");
    }
}
