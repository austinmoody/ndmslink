package com.lantanagroup.link.api.model;

import lombok.Getter;
import lombok.Setter;
import org.hl7.fhir.r4.model.Address;

import javax.annotation.PostConstruct;
import javax.validation.constraints.NotBlank;

@Getter
@Setter
public class LinkLocationAddress {
    @NotBlank(message = "Location Address Line 1 must be specified")
    private String line1;
    private String line2;
    @NotBlank(message = "Location Address City must be specified")
    private String city;
    @NotBlank(message = "Location Address State must be specified")
    private String state;
    private String country;
    @NotBlank(message = "Location Address Postal Code must be specified")
    private String postalCode;

    @PostConstruct
    public void init() {
        if (country == null) {
            country = "USA";
        }
    }

    public Address toFhirAddress() {
        Address address = new Address();
        address.setCity(this.city);
        address.setState(this.state);
        address.setCountry(this.country);
        address.setPostalCode(this.postalCode);
        address.addLine(this.line1);
        if ( (line2 != null) && !line2.isEmpty()) {
            address.addLine(this.line2);
        }
        address.setText("TODO");
        address.setType(Address.AddressType.BOTH);
        address.setUse(Address.AddressUse.WORK);

        return address;
    }
}
