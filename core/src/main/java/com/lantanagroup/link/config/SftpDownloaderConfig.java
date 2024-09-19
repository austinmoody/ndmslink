package com.lantanagroup.link.config;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;

@Getter
@Setter
public class SftpDownloaderConfig {

    private String knownHostsFile;
    private String knownHostsString;

    @NotEmpty
    private String host;

    @Min(0)
    @Max(0xffff)
    private int port = 22;

    @NotEmpty
    private String path;

    @NotEmpty
    private String username;

    @NotEmpty
    private String password;

    private String fileName;

    public boolean isKnownHostsPresent() {
        return (  (knownHostsFile != null && !knownHostsFile.isEmpty()) ||
                  (knownHostsString != null && !knownHostsString.isEmpty())
               );
    }
}
