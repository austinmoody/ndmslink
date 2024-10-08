package com.lantanagroup.link;

import com.lantanagroup.link.auth.LinkCredentials;
import com.lantanagroup.link.config.api.ApiConfig;
import com.lantanagroup.link.model.ReportContext;
import com.lantanagroup.link.model.ReportCriteria;

import java.text.ParseException;
import java.util.concurrent.ExecutionException;

public interface IMeasureGenerator {
    void generate(StopwatchManager stopwatchManager, ReportContext reportContext, ReportContext.MeasureContext measureContext, ReportCriteria criteria, ApiConfig config, LinkCredentials user, IReportAggregator reportAggregator) throws ParseException, ExecutionException, InterruptedException;
    void store(ReportContext.MeasureContext measureContext, ReportContext reportContext);
}
