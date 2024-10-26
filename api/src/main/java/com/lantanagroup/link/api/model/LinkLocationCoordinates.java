package com.lantanagroup.link.api.model;

import lombok.Getter;
import lombok.Setter;
import org.hl7.fhir.r4.model.Location;

import javax.validation.constraints.NotNull;

@Getter
@Setter
public class LinkLocationCoordinates {
    @NotNull(message = "Location Latitude must be specified")
    private Double latitude;
    @NotNull(message = "Location Longitude must be specified")
    private Double longitude;

    public Location.LocationPositionComponent toFhirLocationPositionComponent() {
        Location.LocationPositionComponent locationPositionComponent = new Location.LocationPositionComponent();
        locationPositionComponent.setLatitude(this.latitude);
        locationPositionComponent.setLongitude(this.longitude);
        return locationPositionComponent;
    }
}
