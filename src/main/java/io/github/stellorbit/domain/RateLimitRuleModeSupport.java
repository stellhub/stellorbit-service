package io.github.stellorbit.domain;

import io.github.stellorbit.api.error.InvalidRuleRequestException;
import java.util.Locale;
import java.util.Set;

public final class RateLimitRuleModeSupport {

  public static final String EXECUTION_LOCATION_APPLICATION = "APPLICATION";
  public static final String EXECUTION_LOCATION_SIDECAR = "SIDECAR";
  public static final String EXECUTION_LOCATION_GATEWAY = "GATEWAY";
  public static final String EXECUTION_LOCATION_EDGE = "EDGE";
  public static final String COORDINATION_MODE_LOCAL_ONLY = "LOCAL_ONLY";
  public static final String COORDINATION_MODE_GLOBAL_SYNC = "GLOBAL_SYNC";
  public static final String COORDINATION_MODE_GLOBAL_QUOTA = "GLOBAL_QUOTA";
  public static final String LEGACY_ENFORCEMENT_LOCAL = "LOCAL";
  public static final String LEGACY_ENFORCEMENT_EDGE = "EDGE";

  private static final Set<String> EXECUTION_LOCATIONS =
      Set.of(
          EXECUTION_LOCATION_APPLICATION,
          EXECUTION_LOCATION_SIDECAR,
          EXECUTION_LOCATION_GATEWAY,
          EXECUTION_LOCATION_EDGE);
  private static final Set<String> COORDINATION_MODES =
      Set.of(
          COORDINATION_MODE_LOCAL_ONLY,
          COORDINATION_MODE_GLOBAL_SYNC,
          COORDINATION_MODE_GLOBAL_QUOTA);

  private RateLimitRuleModeSupport() {}

  /** 归一化限流执行位置，并兼容旧enforcementMode字段。 */
  public static String normalizeExecutionLocation(
      String executionLocation, String enforcementMode) {
    if (hasText(executionLocation)) {
      return requireAllowed(
          executionLocation,
          EXECUTION_LOCATIONS,
          "executionLocation只支持APPLICATION/SIDECAR/GATEWAY/EDGE");
    }
    String legacyMode = normalizeText(enforcementMode);
    if (LEGACY_ENFORCEMENT_EDGE.equals(legacyMode)) {
      return EXECUTION_LOCATION_EDGE;
    }
    return EXECUTION_LOCATION_APPLICATION;
  }

  /** 归一化限流全局协调方式，并兼容旧enforcementMode字段。 */
  public static String normalizeCoordinationMode(String coordinationMode, String enforcementMode) {
    if (hasText(coordinationMode)) {
      return requireAllowed(
          coordinationMode,
          COORDINATION_MODES,
          "coordinationMode只支持LOCAL_ONLY/GLOBAL_SYNC/GLOBAL_QUOTA");
    }
    String legacyMode = normalizeText(enforcementMode);
    if (COORDINATION_MODE_GLOBAL_SYNC.equals(legacyMode)
        || COORDINATION_MODE_GLOBAL_QUOTA.equals(legacyMode)) {
      return legacyMode;
    }
    return COORDINATION_MODE_LOCAL_ONLY;
  }

  /** 根据新模型生成旧enforcementMode，供旧客户端和旧数据列兼容使用。 */
  public static String toLegacyEnforcementMode(String executionLocation, String coordinationMode) {
    String normalizedLocation = normalizeExecutionLocation(executionLocation, null);
    String normalizedCoordination = normalizeCoordinationMode(coordinationMode, null);
    if (!COORDINATION_MODE_LOCAL_ONLY.equals(normalizedCoordination)) {
      return normalizedCoordination;
    }
    if (EXECUTION_LOCATION_EDGE.equals(normalizedLocation)) {
      return LEGACY_ENFORCEMENT_EDGE;
    }
    return LEGACY_ENFORCEMENT_LOCAL;
  }

  /** 判断规则是否需要分布式限流服务端参与全局协调。 */
  public static boolean isDistributedCoordination(String coordinationMode) {
    String normalized = normalizeCoordinationMode(coordinationMode, null);
    return COORDINATION_MODE_GLOBAL_SYNC.equals(normalized)
        || COORDINATION_MODE_GLOBAL_QUOTA.equals(normalized);
  }

  private static String requireAllowed(String value, Set<String> allowed, String message) {
    String normalized = normalizeText(value);
    if (!allowed.contains(normalized)) {
      throw new InvalidRuleRequestException(message + ": " + value);
    }
    return normalized;
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private static String normalizeText(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }
}
