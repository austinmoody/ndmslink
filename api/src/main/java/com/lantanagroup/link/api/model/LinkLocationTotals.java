package com.lantanagroup.link.api.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class LinkLocationTotals {
    private String id;
    private String measure;
    private List<LinkLocationTotalsGroup> groups;

    @Getter
    @Setter
    private static class LinkLocationTotalsPopulation {
        private String code;
        private String system;
        private int count;
    }
}

