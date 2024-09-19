package com.lantanagroup.link.thsa.parkland;

import com.ainq.saner.converters.csv.CsvToReportConverter;
import com.lantanagroup.link.FhirContextProvider;
import com.lantanagroup.link.FhirDataProvider;
import com.lantanagroup.link.IUploadFileToMeasureReport;
import com.lantanagroup.link.config.thsa.THSAConfig;
import com.lantanagroup.link.model.UploadFile;
import com.opencsv.CSVReaderHeaderAware;
import com.opencsv.CSVWriter;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Measure;
import org.hl7.fhir.r4.model.MeasureReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.*;

@Component
public class ParklandCsvToMeasureReport implements IUploadFileToMeasureReport {
    private static final Logger logger = LoggerFactory.getLogger(ParklandCsvToMeasureReport.class);

    @Autowired
    private THSAConfig thsaConfig;

    public MeasureReport convert(UploadFile uploadFile, FhirDataProvider fhirDataProvider) throws Exception {
        MeasureReport measureReport = new MeasureReport();

        Measure measure = new Measure();
        try(InputStream measureStream = getClass().getClassLoader().getResourceAsStream("THSAMasterAggregate.xml")) {
            measure = FhirContextProvider.getFhirContext().newXmlParser().parseResource(Measure.class, measureStream);
        } catch(IOException ex){
            logger.error("Error retrieving measure in THSA data processor: " + ex.getMessage());
            throw ex;
        }

        byte[] decodedContent = Base64.getDecoder().decode(uploadFile.getContent());
        byte[] convertedContent = ConvertParklandBedCsv(decodedContent, (ArrayList<String>) uploadFile.getOptions().get("icu-codes"));

        try(InputStream contentStream = new ByteArrayInputStream(convertedContent)) {
            Reader reader = new InputStreamReader(contentStream);

            CsvToReportConverter converter = new CsvToReportConverter(measure, null, null);
            try {
                measureReport = converter.convert(reader);
            } catch (IOException e) {
                logger.error(e.getMessage());
                throw e;
            }

            measureReport.setId(this.thsaConfig.getBedInventoryReportId());

            // Store report
            Bundle updateBundle = new Bundle();
            updateBundle.setType(Bundle.BundleType.TRANSACTION);
            updateBundle.addEntry()
                    .setResource(measureReport)
                    .setRequest(new Bundle.BundleEntryRequestComponent()
                            .setMethod(Bundle.HTTPVerb.PUT)
                            .setUrl("MeasureReport/" + this.thsaConfig.getBedInventoryReportId()));
            fhirDataProvider.transaction(updateBundle);
        }
        catch(IOException ex) {
            logger.error("Error converting measure in THSA data processor: " + ex.getMessage());
            throw ex;
        }
        return measureReport;
    }

    private byte[] CreateBedInventoryCsv(int totalBeds, int icuBeds) throws Exception {
        /*
            The headers for the file that the /api/data/csv API endpoint accepts:

            numTotBedsOcc,numTotBedsAvail,numTotBeds,numICUBedsOcc,numICUBedsAvail,numICUBeds,numVentUse,numVentAvail,numVent

            This is what we are going to re-create.
        */

        logger.info("Creating Bed Inventory CSV - Total Beds: {}, Total ICU Beds: {}", totalBeds, icuBeds);

        StringWriter stringWriter = new StringWriter();

        try (CSVWriter writer = new CSVWriter(stringWriter)) {

            //Array of header
            String[] header = { "numTotBedsOcc",
                    "numTotBedsAvail",
                    "numTotBeds",
                    "numICUBedsOcc",
                    "numICUBedsAvail",
                    "numICUBeds",
                    "numVentUse",
                    "numVentAvail",
                    "numVent" };
            writer.writeNext(header);

            //Array of data
            String[] data = { "0", "0", String.valueOf(totalBeds), "0", "0", String.valueOf(icuBeds), "0", "0", "0" };

            //Writing data
            writer.writeNext(data);

            String csvContent = stringWriter.toString();
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            byteArrayOutputStream.writeBytes(csvContent.getBytes());

            return byteArrayOutputStream.toByteArray();

        } catch (IOException ex) {
            logger.error("Issue creating Bed Inventory CSV: {}", ex.getMessage());
            throw ex;
        }
    }

    private byte[] ConvertParklandBedCsv(byte[] csvData, ArrayList<String> icuSpecialFacs) throws Exception {
        try {
            logger.info("Parkland Bed CSV Conversion - Started");
            InputStream contentStream = new ByteArrayInputStream(csvData);
            Reader reader = new InputStreamReader(contentStream);
            CSVReaderHeaderAware csvReader = new CSVReaderHeaderAware(reader);

            logger.info("ICU Facility Identifiers: {}", String.join(",", icuSpecialFacs));

            int totalRecords=0;
            int icuRecords=0;

            Map<String,String> record;
            while ( (record = csvReader.readMap()) != null) {
                totalRecords++;
                if (icuSpecialFacs.contains(record.get("Special Facs"))) {
                    icuRecords++;
                }
            }

            logger.info("Total Records: {}", totalRecords);
            logger.info("Total ICU Records: {}", icuRecords);

            logger.info("Parkland Bed CSV Conversion - Completed");

            return CreateBedInventoryCsv( (totalRecords - icuRecords), icuRecords);
        } catch (Exception ex) {
            logger.error("Issue converting Parkland bed CSV file: {}", ex.getMessage());
            throw ex;
        }
    }
}
