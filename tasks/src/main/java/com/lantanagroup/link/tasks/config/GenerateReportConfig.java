package com.lantanagroup.link.tasks.config;

import com.lantanagroup.link.config.YamlPropertySourceFactory;
import com.lantanagroup.link.config.auth.LinkOAuthConfig;
import com.lantanagroup.link.tasks.helpers.EndDateAdjuster;
import com.lantanagroup.link.tasks.helpers.StartDateAdjuster;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter

@Configuration
@ConfigurationProperties(prefix = "cli.generate-report")
@Validated
@PropertySource(value = "classpath:application.yml", factory = YamlPropertySourceFactory.class)
public class GenerateReportConfig {
    private String apiUrl;
    private String[] bundleIds;
    private StartDateAdjuster startDate;
    private EndDateAdjuster endDate;
    private LinkOAuthConfig auth;
}
