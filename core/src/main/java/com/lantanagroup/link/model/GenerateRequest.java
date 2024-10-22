package com.lantanagroup.link.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GenerateRequest {
  boolean regenerate;
  private String[] bundleIds;
  private String periodStart;
  private String periodEnd;
  private String locationId;
  // TODO: Change bundleIds to just a measureId when I get there....
}
