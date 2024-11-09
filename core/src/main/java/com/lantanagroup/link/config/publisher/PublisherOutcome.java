package com.lantanagroup.link.config.publisher;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PublisherOutcome {
    private boolean success;
    private String publishedLocation;
    private String message;
}
