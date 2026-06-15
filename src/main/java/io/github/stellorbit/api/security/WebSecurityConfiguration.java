package io.github.stellorbit.api.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebSecurityConfiguration implements WebMvcConfigurer {

  private final ControlPlaneSecurityInterceptor controlPlaneSecurityInterceptor;

  public WebSecurityConfiguration(ControlPlaneSecurityInterceptor controlPlaneSecurityInterceptor) {
    this.controlPlaneSecurityInterceptor = controlPlaneSecurityInterceptor;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(controlPlaneSecurityInterceptor).addPathPatterns("/api/stellorbit/**");
  }
}
