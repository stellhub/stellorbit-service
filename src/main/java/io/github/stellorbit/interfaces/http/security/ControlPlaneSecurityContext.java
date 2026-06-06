package io.github.stellorbit.interfaces.http.security;

import java.util.Set;
import java.util.UUID;

public record ControlPlaneSecurityContext(
    String tenantId,
    UUID instanceSpaceId,
    String operator,
    Set<String> roles,
    String reason,
    String requestId,
    String userAgent,
    String remoteAddress) {

  public boolean hasAnyRole(String... requiredRoles) {
    if (roles.contains("ADMIN")) {
      return true;
    }
    for (String role : requiredRoles) {
      if (roles.contains(role)) {
        return true;
      }
    }
    return false;
  }
}
