package com.lantanagroup.link.ndms.methodist;

import com.opencsv.bean.CsvBindByName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MethodistDataModel {

    @CsvBindByName(column = "Facility")
    private String facility;

    @CsvBindByName(column = "Unit")
    private String unit;

    @CsvBindByName(column = "Room")
    private String room;

    @CsvBindByName(column = "Bed")
    private String bed;

    @CsvBindByName(column = "Bed Status")
    private String bedStatus;

    @CsvBindByName(column = "Active/Inactive")
    private String active;

    // Methodist has the TRAC2ES mapped data in a column named Code.  I had originally added a trac2es field
    // to this model, but wasn't reading it in from CSV and only had it there so that I could map it.  Now
    // that I don't have to map it I am setting the CSV field Code to that trac2es.  As there is code in the
    // processor that is already built to deal with that field having data.
    @CsvBindByName(column = "Code")
    private String trac2es;

    @CsvBindByName(column = "Neg Airflow")
    private String negAirflow;
}
