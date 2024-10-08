package com.lantanagroup.link.ndms;

import com.lantanagroup.link.IReportAggregator;
import com.lantanagroup.link.model.ReportContext;
import com.lantanagroup.link.model.ReportCriteria;
import org.hl7.fhir.r4.model.MeasureReport;
import org.springframework.stereotype.Component;

import java.text.ParseException;

@Component
public class NdmsAggregator implements IReportAggregator {
    @Override
    public MeasureReport generate(ReportCriteria criteria, ReportContext reportContext, ReportContext.MeasureContext measureContext) throws ParseException {
        return new MeasureReport();
    }
}
