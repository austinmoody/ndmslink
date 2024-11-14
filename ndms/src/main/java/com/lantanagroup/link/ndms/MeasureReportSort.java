package com.lantanagroup.link.ndms;

/*
Purpose of this is to sort the group in a MeasureReport a specific way
for ArcGIS.
 */

import org.hl7.fhir.r4.model.MeasureReport;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MeasureReportSort {

    private MeasureReportSort() {
        throw new IllegalStateException("Utility class");
    }

    private static final List<String> GROUP_ORDER = Arrays.asList(
            "CC", "MM-SS", "MP", "SBN", "MC", "PICU", "NPU", "Beds"
    );

    public static void sortMeasureReportGroups(MeasureReport report) {
        List<MeasureReport.MeasureReportGroupComponent> groups = new ArrayList<>(report.getGroup());

        groups.sort((g1, g2) -> {
            String code1 = getGroupCode(g1);
            String code2 = getGroupCode(g2);

            int index1 = GROUP_ORDER.indexOf(code1);
            int index2 = GROUP_ORDER.indexOf(code2);

            // Handle cases where codes don't exist in our predefined TRAC2ES ordered list
            if (index1 == -1) index1 = Integer.MAX_VALUE;
            if (index2 == -1) index2 = Integer.MAX_VALUE;

            return Integer.compare(index1, index2);
        });

        report.setGroup(groups);
    }

    private static String getGroupCode(MeasureReport.MeasureReportGroupComponent group) {
        if (group.hasCode() &&
                group.getCode().hasCoding() &&
                !group.getCode().getCoding().isEmpty()) {
            return group.getCode().getCodingFirstRep().getCode();
        }
        return "";
    }
}
