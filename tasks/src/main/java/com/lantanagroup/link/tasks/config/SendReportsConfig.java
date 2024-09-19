package com.lantanagroup.link.tasks.config;

import com.lantanagroup.link.config.YamlPropertySourceFactory;
import com.lantanagroup.link.config.auth.LinkOAuthConfig;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter

@Configuration
@ConfigurationProperties(prefix = "cli.send-reports")
@Validated
@PropertySource(value = "classpath:application.yml", factory = YamlPropertySourceFactory.class)
public class SendReportsConfig {
    private String searchReportsUrl;
    private String sendReportUrl;
    private LinkOAuthConfig auth;
}
