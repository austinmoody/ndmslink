package com.lantanagroup.link.ndms;

import org.hl7.fhir.r4.model.Coding;

public class NdmsConstants {
    // TODO: Look this up in ConceptMap?  Similar to how we are doing the TRAC2ES to Population lookup in NdmsAggregator
    public static final Coding NDMS_OVERALL_OCC_CODE = new Coding("https://thsa1.sanerproject.org:10443/fhir/CodeSystem/NdmsMeasuredValues","numTotBedsOcc","Hospital Beds Occupied");
    public static final Coding NDMS_OVERALL_AVAIL_CODE = new Coding("https://thsa1.sanerproject.org:10443/fhir/CodeSystem/NdmsMeasuredValues","numTotBedsAvail","Hospital Beds Available");
}
