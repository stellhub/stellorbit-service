package io.github.stellorbit.infrastructure.stellnula;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "stellorbit.stellnula")
public class StellnulaProperties {

  private String baseUrl = "http://127.0.0.1:8060";

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }
}
