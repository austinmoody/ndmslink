package com.lantanagroup.link.thsa;

import com.lantanagroup.link.*;
import com.lantanagroup.link.auth.LinkCredentials;
import com.lantanagroup.link.config.api.ApiConfig;
import com.lantanagroup.link.model.ReportContext;
import com.lantanagroup.link.model.ReportCriteria;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.MeasureReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

public class ThsaMeasureReportGenerator implements IMeasureReportGenerator {
    private static final Logger logger = LoggerFactory.getLogger(ThsaMeasureReportGenerator.class);
    @Override
    public void generate(StopwatchManager stopwatchManager,
                         ReportContext reportContext,
                         ReportContext.MeasureContext measureContext,
                         ReportCriteria criteria,
                         ApiConfig config,
                         LinkCredentials user,
                         IReportAggregator reportAggregator) throws ParseException, ExecutionException, InterruptedException, Exception {
        if (config.getEvaluationService() == null) {
            throw new IllegalStateException("api.evaluation-service has not been configured");
        }

        logger.info("Patient list is : " + measureContext.getPatientsOfInterest().size());
        ForkJoinPool forkJoinPool = config.getMeasureEvaluationThreads() != null
                ? new ForkJoinPool(config.getMeasureEvaluationThreads())
                : ForkJoinPool.commonPool();

        try {
            List<MeasureReport> patientMeasureReports = forkJoinPool.submit(() ->
                    measureContext.getPatientsOfInterest().parallelStream().filter(patient -> !StringUtils.isEmpty(patient.getId())).map(patient -> {

                        logger.info("Generating measure report for patient " + patient);
                        MeasureReport patientMeasureReport = new MeasureReport();
                        try {
                            patientMeasureReport = MeasureEvaluator.generateMeasureReport(stopwatchManager, criteria, reportContext, measureContext, config, patient);
                        } catch (Exception ex) {
                            logger.error(String.format("Issue generating patient measure report for %s, error %s", patient, ex.getMessage()));
                        }

                        String measureReportId = ReportIdHelper.getPatientMeasureReportId(measureContext.getReportId(), patient.getId());
                        patientMeasureReport.setId(measureReportId);
                        // Tag individual MeasureReport as patient-data as it references a patient and will be found for expunge
                        patientMeasureReport.getMeta().addTag(Constants.MainSystem, Constants.patientDataTag,"");

                        logger.info(String.format("Persisting patient %s measure report with id %s", patient, measureReportId));
                        Stopwatch stopwatch = stopwatchManager.start("store-measure-report");
                        reportContext.getFhirProvider().updateResource(patientMeasureReport);
                        stopwatch.stop();

                        return patientMeasureReport;
                    }).collect(Collectors.toList())).get();
            // to avoid thread collision remove saving the patientMeasureReport on the FhirServer from the above parallelStream
            // pass them to aggregators using measureContext
            measureContext.setPatientReports(patientMeasureReports);
        } finally {
            if (forkJoinPool != null) {
                forkJoinPool.shutdown();
            }
        }
        MeasureReport masterMeasureReport = reportAggregator.generate(criteria, reportContext, measureContext, config);
        measureContext.setMeasureReport(masterMeasureReport);

    }

    @Override
    public void store(ReportContext.MeasureContext measureContext, ReportContext reportContext) {
        logger.info("Storing measure report");
    }
}
