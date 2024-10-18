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
public class NhsnLocation {
    private String organizationId;
    private String code;
    private String unit;
    private String cdcCode;
    private String locationCode;
    private String cdcLabel;
    private String status;
    private String trac2es;

    private static final Logger logger = LoggerFactory.getLogger(NhsnLocation.class);

    public NhsnLocation(String organizationId, String code, String unit, String cdcCode,
                        String locationCode, String cdcLabel, String status, String trac2es) {
        this.organizationId = organizationId;
        this.code = code;
        this.unit = unit;
        this.cdcCode = cdcCode;
        this.locationCode = locationCode;
        this.cdcLabel = cdcLabel;
        this.status = status;
        this.trac2es = trac2es;
    }

    public static List<NhsnLocation> parseCsvData(String csvData) {
        List<NhsnLocation> locations = new ArrayList<>();
        String[] lines = csvData.split("\n");

        Pattern pattern = Pattern.compile(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");

        // Assume there is a header so skip it
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            String[] fields = pattern.split(line, -1);

            if (fields.length == 8) {
                for (int j = 0; j < fields.length; j++) {
                    fields[j] = fields[j].replaceAll("^\"|\"$", "").trim();
                }
                NhsnLocation location = new NhsnLocation(
                        fields[0], fields[1], fields[2], fields[3], fields[4], fields[5], fields[6], fields[7]
                );
                locations.add(location);
            } else {
                logger.error("Skipping bad line: {}", line);
            }
        }

        return locations;
    }
}
