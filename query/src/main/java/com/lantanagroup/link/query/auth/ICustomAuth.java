package com.lantanagroup.link.query.auth;

public interface ICustomAuth {
  void setConfig(ICustomAuthConfig authConfig) throws Exception;
  String getAuthHeader() throws Exception;
  String getApiKeyHeader() throws Exception;
}
