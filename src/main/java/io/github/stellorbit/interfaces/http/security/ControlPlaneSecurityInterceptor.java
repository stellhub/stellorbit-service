package io.github.stellorbit.interfaces.http.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class ControlPlaneSecurityInterceptor implements HandlerInterceptor {

  private static final Pattern UUID_PATTERN =
      Pattern.compile(
          "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

  private final ControlPlaneAuditService auditService;

  public ControlPlaneSecurityInterceptor(ControlPlaneAuditService auditService) {
    this.auditService = auditService;
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (!isControlPlaneRequest(request)) {
      return true;
    }
    ControlPlaneSecurityContext context = buildContext(request);
    ControlPlaneSecurityContextHolder.set(context);
    validateIdentity(context, request);
    validateRbac(context, request);
    return true;
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request,
      HttpServletResponse response,
      Object handler,
      Exception exception) {
    try {
      if (isControlPlaneRequest(request) && isMutation(request)) {
        recordHttpAudit(request, response, exception);
      }
    } finally {
      ControlPlaneSecurityContextHolder.clear();
    }
  }

  private ControlPlaneSecurityContext buildContext(HttpServletRequest request) {
    return new ControlPlaneSecurityContext(
        header(request, "X-Stellorbit-Tenant-Id"),
        uuidHeader(request, "X-Stellorbit-Instance-Space-Id"),
        header(request, "X-Stellorbit-Operator"),
        roles(header(request, "X-Stellorbit-Roles")),
        header(request, "X-Stellorbit-Reason"),
        header(request, "X-Request-Id"),
        request.getHeader("User-Agent"),
        remoteAddress(request));
  }

  private void validateIdentity(ControlPlaneSecurityContext context, HttpServletRequest request) {
    requireText(context.tenantId(), "缺少租户身份: X-Stellorbit-Tenant-Id");
    requireText(context.operator(), "缺少操作人身份: X-Stellorbit-Operator");
    if (context.roles().isEmpty()) {
      throw new AccessDeniedException("缺少角色信息: X-Stellorbit-Roles");
    }
    if (requiresInstanceSpaceScope(request) && context.instanceSpaceId() == null) {
      throw new AccessDeniedException("缺少数据权限作用域: X-Stellorbit-Instance-Space-Id");
    }
    if (isMutation(request)) {
      requireText(context.reason(), "写操作必须提供操作原因: X-Stellorbit-Reason");
    }
  }

  private void validateRbac(ControlPlaneSecurityContext context, HttpServletRequest request) {
    String method = request.getMethod();
    String path = request.getRequestURI();
    if ("GET".equalsIgnoreCase(method)) {
      requireRole(context, "VIEWER", "OPERATOR", "PUBLISHER", "APPROVER", "SECURITY_ADMIN");
      return;
    }
    if (path.contains("/approvals/approve") || path.contains("/approvals/reject")) {
      requireRole(context, "APPROVER");
      return;
    }
    if (path.contains("/rule-releases")) {
      requireRole(context, "PUBLISHER");
      return;
    }
    if (path.contains("/security/")) {
      requireRole(context, "SECURITY_ADMIN", "OPERATOR");
      return;
    }
    requireRole(context, "OPERATOR");
  }

  private void recordHttpAudit(
      HttpServletRequest request, HttpServletResponse response, Exception exception) {
    ControlPlaneSecurityContext context = ControlPlaneSecurityContextHolder.required();
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("method", request.getMethod());
    detail.put("path", request.getRequestURI());
    detail.put("query", request.getQueryString());
    detail.put("httpStatus", response.getStatus());
    detail.put("success", exception == null && response.getStatus() < 400);
    if (exception != null) {
      detail.put("exception", exception.getClass().getSimpleName());
      detail.put("message", exception.getMessage());
    }
    auditService.record(
        "CONTROL_PLANE_HTTP_MUTATION",
        resourceType(request),
        resourceId(request),
        context.instanceSpaceId(),
        null,
        context.operator(),
        context.reason(),
        detail);
  }

  private boolean isControlPlaneRequest(HttpServletRequest request) {
    String path = request.getRequestURI();
    return path.startsWith("/api/stellorbit/")
        && !path.startsWith("/api/stellorbit/runtime/")
        && !"OPTIONS".equalsIgnoreCase(request.getMethod());
  }

  private boolean requiresInstanceSpaceScope(HttpServletRequest request) {
    String path = request.getRequestURI();
    return !path.startsWith("/api/stellorbit/instance-spaces");
  }

  private boolean isMutation(HttpServletRequest request) {
    return switch (request.getMethod().toUpperCase(Locale.ROOT)) {
      case "POST", "PUT", "PATCH", "DELETE" -> true;
      default -> false;
    };
  }

  private void requireRole(ControlPlaneSecurityContext context, String... roles) {
    if (!context.hasAnyRole(roles)) {
      throw new AccessDeniedException("当前角色无权执行该操作");
    }
  }

  private void requireText(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new AccessDeniedException(message);
    }
  }

  private String header(HttpServletRequest request, String name) {
    String value = request.getHeader(name);
    return value == null || value.isBlank() ? null : value.trim();
  }

  private UUID uuidHeader(HttpServletRequest request, String name) {
    String value = header(request, name);
    if (value == null) {
      return null;
    }
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException exception) {
      throw new AccessDeniedException("非法数据权限作用域: " + name);
    }
  }

  private Set<String> roles(String rolesHeader) {
    if (rolesHeader == null || rolesHeader.isBlank()) {
      return Set.of();
    }
    return Arrays.stream(rolesHeader.split(","))
        .map(String::trim)
        .filter(role -> !role.isBlank())
        .map(role -> role.toUpperCase(Locale.ROOT))
        .collect(Collectors.toUnmodifiableSet());
  }

  private String remoteAddress(HttpServletRequest request) {
    String forwardedFor = request.getHeader("X-Forwarded-For");
    if (forwardedFor != null && !forwardedFor.isBlank()) {
      return forwardedFor.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }

  private String resourceType(HttpServletRequest request) {
    String[] parts = request.getRequestURI().split("/");
    if (parts.length >= 4) {
      return parts[3].replace('-', '_').toUpperCase(Locale.ROOT);
    }
    return "CONTROL_PLANE";
  }

  private UUID resourceId(HttpServletRequest request) {
    Matcher matcher = UUID_PATTERN.matcher(request.getRequestURI());
    if (!matcher.find()) {
      return null;
    }
    return UUID.fromString(matcher.group());
  }
}
