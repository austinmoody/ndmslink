package com.lantanagroup.link.ndms;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/*
 NOTE: This is likely a temporary thing.  I have a spreadsheet from Nebraska Med that maps their bed types
 to a "service location code" like 1026-4.  That can be later mapped to TRA2CES codes which will land
 in the MeasureReport.  Here I'm just creating a way to read in a CSV export of that spreadsheet into a List
 of Objects that I can use to map what I'm finding in data pulled from their system.

 Have an idea to create FHIR Locations out of each row and store those on the Data Store and then query them as
 part of mapping.  So we don't have to have CSV's around places.  Will get there if we can.
 */

@Getter
@Setter
public class NebraskaMedicalLocation {
    private String orgId;
    private String yourCode;
    private String unitLabel;
    private String cdcCode;
    private String nhsnHealthcareServiceLocationCode;
    private String cdcLocationLabel;
    private String status;

    private static final Logger logger = LoggerFactory.getLogger(NebraskaMedicalLocation.class);

    public NebraskaMedicalLocation(String orgId, String yourCode, String unitLabel, String cdcCode,
                                   String nhsnHealthcareServiceLocationCode, String cdcLocationLabel, String status) {
        this.orgId = orgId;
        this.yourCode = yourCode;
        this.unitLabel = unitLabel;
        this.cdcCode = cdcCode;
        this.nhsnHealthcareServiceLocationCode = nhsnHealthcareServiceLocationCode;
        this.cdcLocationLabel = cdcLocationLabel;
        this.status = status;
    }

    public static List<NebraskaMedicalLocation> parseCsvData(String csvData) {
        List<NebraskaMedicalLocation> locations = new ArrayList<>();
        String[] lines = csvData.split("\n");

        Pattern pattern = Pattern.compile(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");

        // Assume there is a header so skip it
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            String[] fields = pattern.split(line, -1);// line.split(",");

            if (fields.length == 7) {
                for (int j = 0; j < fields.length; j++) {
                    fields[j] = fields[j].replaceAll("^\"|\"$", "").trim();
                }
                NebraskaMedicalLocation location = new NebraskaMedicalLocation(
                        fields[0], fields[1], fields[2], fields[3], fields[4], fields[5], fields[6]
                );
                locations.add(location);
            } else {
                logger.error("Skipping bad line: {}", line);
            }
        }

        return locations;
    }
}
