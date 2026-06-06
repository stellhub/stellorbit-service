package io.github.stellorbit.interfaces.http.security;

import java.util.Optional;
import java.util.UUID;

public final class ControlPlaneSecurityContextHolder {

  private static final ThreadLocal<ControlPlaneSecurityContext> CONTEXT = new ThreadLocal<>();

  private ControlPlaneSecurityContextHolder() {}

  public static void set(ControlPlaneSecurityContext context) {
    CONTEXT.set(context);
  }

  public static Optional<ControlPlaneSecurityContext> current() {
    return Optional.ofNullable(CONTEXT.get());
  }

  public static ControlPlaneSecurityContext required() {
    return current().orElseThrow(() -> new AccessDeniedException("缺少控制面安全上下文"));
  }

  public static String operator() {
    return required().operator();
  }

  public static String tenantId() {
    return required().tenantId();
  }

  public static String reason() {
    return required().reason();
  }

  public static UUID requiredInstanceSpaceId() {
    UUID instanceSpaceId = required().instanceSpaceId();
    if (instanceSpaceId == null) {
      throw new AccessDeniedException("缺少数据权限作用域: X-Stellorbit-Instance-Space-Id");
    }
    return instanceSpaceId;
  }

  public static void requireInstanceSpace(UUID instanceSpaceId) {
    UUID requiredInstanceSpaceId = requiredInstanceSpaceId();
    if (!requiredInstanceSpaceId.equals(instanceSpaceId)) {
      throw new AccessDeniedException("无权访问当前实例空间数据");
    }
  }

  public static void requireTenant(String tenantId) {
    String requiredTenantId = tenantId();
    if (!requiredTenantId.equals(tenantId)) {
      throw new AccessDeniedException("无权访问当前租户数据");
    }
  }

  public static void clear() {
    CONTEXT.remove();
  }
}
