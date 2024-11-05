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

    private String trac2es;
}
