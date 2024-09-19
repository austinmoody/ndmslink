package com.lantanagroup.link;

import com.lantanagroup.link.model.UploadFile;
import org.hl7.fhir.r4.model.MeasureReport;

public interface IUploadFileToMeasureReport {
    MeasureReport convert(UploadFile uploadFile, FhirDataProvider fhirDataProvider) throws Exception;
}
