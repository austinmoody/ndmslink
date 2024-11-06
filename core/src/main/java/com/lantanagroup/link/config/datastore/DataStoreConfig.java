package com.lantanagroup.link.config.datastore;

import com.lantanagroup.link.config.DataSourceConfig;
import com.lantanagroup.link.config.YamlPropertySourceFactory;
import com.lantanagroup.link.config.api.CorsConfig;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.AssertTrue;
import java.util.HashMap;
import java.util.List;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "datastore")
@Validated
@PropertySource(value = "classpath:application.yml", factory = YamlPropertySourceFactory.class)
public class DataStoreConfig {
  /**
   * <strong>datastore.data-source</strong><br/>Persistence configuration (i.e. Postgres connection information) for the data store
   */
  private DataSourceConfig dataSource;

  /**
   * <strong>datastore.oauth</strong><br/>OAuth configuration of the data store
   */
  private DataStoreOAuthConfig oauth;

  /**
   * <strong>datastore.basic-auth-users</strong><br/>Key-Value pair of usernames and passwords that have access to the data store
   */
  private HashMap<String, String> basicAuthUsers;

  /**
   * <strong>datastore.public-address</strong><br/>The public address that the data store is exposed at.
   */
  private String publicAddress;

  /**
   * <strong>datastore.cors</strong><br>CORS configuration used for browser interaction with the Data Store
   */
  private CorsConfig cors;

  private DataStoreDaoConfig dao;

  private boolean ipFilterEnabled = true;

  private List<String> allowedIps;

  @AssertTrue(message = "allowed-ips must not be empty when ip-filter-enabled is true")
  private boolean isValidIpConfiguration() {
    return !ipFilterEnabled || (allowedIps != null && !allowedIps.isEmpty());
  }
}
