package io.github.stellorbit.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class RateLimitRuleModelSupport {

  public static final Set<String> LIMIT_MODES =
      Set.of(
          "QPS",
          "CONCURRENCY",
          "HEADER",
          "HOT_KEY",
          "CUSTOM",
          "QUOTA",
          "BANDWIDTH",
          "CONNECTION",
          "MODEL");
  public static final Set<String> LIMIT_TYPES =
      Set.of(
          "REQUEST",
          "CONNECTION",
          "BYTE",
          "TENANT",
          "USER",
          "CALLER",
          "API_KEY",
          "RESOURCE",
          "HEADER",
          "GRPC_METADATA",
          "IP",
          "ENDPOINT",
          "METHOD",
          "TOPIC",
          "MODEL_REQUEST",
          "MODEL_TOKEN",
          "MODEL_COST",
          "MODEL_CONCURRENCY",
          "CUSTOM_KEY");
  public static final Set<String> LIMIT_ALGORITHMS =
      Set.of(
          "TOKEN_BUCKET",
          "LEAKY_BUCKET",
          "FIXED_WINDOW",
          "SLIDING_WINDOW",
          "QUOTA_LEASE",
          "CONCURRENCY_LIMIT",
          "HOT_KEY",
          "CUSTOM",
          "ADAPTIVE");
  public static final Set<String> TRAFFIC_PROTOCOLS =
      Set.of("HTTP", "GRPC", "TCP", "MESSAGE", "MODEL", "ANY");
  public static final Set<String> KEY_EXTRACTOR_SOURCES =
      Set.of(
          "HEADER",
          "GRPC_METADATA",
          "QUERY",
          "COOKIE",
          "PATH",
          "HTTP_METHOD",
          "HTTP_PATH",
          "GRPC_SERVICE",
          "GRPC_METHOD",
          "REMOTE_IP",
          "JWT_CLAIM",
          "API_KEY",
          "TENANT",
          "USER",
          "CALLER",
          "BODY_JSON_PATH",
          "CUSTOM_EXPRESSION");

  private static final Set<String> CUSTOM_POLICY_TYPES =
      Set.of("EXPRESSION", "SCRIPT", "PLUGIN", "EXTERNAL_SERVICE");
  private static final Map<String, Set<String>> MODE_ALGORITHMS =
      Map.of(
          "QPS",
          Set.of("TOKEN_BUCKET", "LEAKY_BUCKET", "FIXED_WINDOW", "SLIDING_WINDOW", "ADAPTIVE"),
          "CONCURRENCY",
          Set.of("CONCURRENCY_LIMIT", "ADAPTIVE"),
          "HEADER",
          Set.of("TOKEN_BUCKET", "LEAKY_BUCKET", "FIXED_WINDOW", "SLIDING_WINDOW", "ADAPTIVE"),
          "HOT_KEY",
          Set.of("HOT_KEY", "TOKEN_BUCKET", "SLIDING_WINDOW", "ADAPTIVE"),
          "CUSTOM",
          Set.of("CUSTOM"),
          "QUOTA",
          Set.of("QUOTA_LEASE"),
          "BANDWIDTH",
          Set.of("TOKEN_BUCKET", "LEAKY_BUCKET", "FIXED_WINDOW", "SLIDING_WINDOW", "ADAPTIVE"),
          "CONNECTION",
          Set.of("CONCURRENCY_LIMIT", "ADAPTIVE"),
          "MODEL",
          Set.of(
              "TOKEN_BUCKET", "FIXED_WINDOW", "SLIDING_WINDOW", "CONCURRENCY_LIMIT", "ADAPTIVE"));
  private static final Map<String, Set<String>> MODE_TYPES =
      Map.of(
          "QPS",
          Set.of(
              "REQUEST",
              "TENANT",
              "USER",
              "CALLER",
              "API_KEY",
              "RESOURCE",
              "HEADER",
              "GRPC_METADATA",
              "IP",
              "ENDPOINT",
              "METHOD",
              "CUSTOM_KEY"),
          "CONCURRENCY",
          Set.of(
              "REQUEST",
              "CONNECTION",
              "TENANT",
              "USER",
              "CALLER",
              "RESOURCE",
              "ENDPOINT",
              "METHOD",
              "MODEL_CONCURRENCY"),
          "HEADER",
          Set.of("HEADER", "GRPC_METADATA", "TENANT", "USER", "CALLER", "API_KEY", "CUSTOM_KEY"),
          "HOT_KEY",
          Set.of(
              "TENANT",
              "USER",
              "CALLER",
              "API_KEY",
              "RESOURCE",
              "HEADER",
              "GRPC_METADATA",
              "IP",
              "ENDPOINT",
              "METHOD",
              "TOPIC",
              "CUSTOM_KEY"),
          "CUSTOM",
          LIMIT_TYPES,
          "QUOTA",
          Set.of(
              "REQUEST",
              "CONNECTION",
              "BYTE",
              "TENANT",
              "USER",
              "CALLER",
              "API_KEY",
              "RESOURCE",
              "ENDPOINT",
              "TOPIC",
              "MODEL_REQUEST",
              "MODEL_TOKEN",
              "MODEL_COST",
              "MODEL_CONCURRENCY",
              "CUSTOM_KEY"),
          "BANDWIDTH",
          Set.of("BYTE", "IP", "ENDPOINT", "USER", "CALLER", "TENANT", "RESOURCE"),
          "CONNECTION",
          Set.of("CONNECTION", "IP", "ENDPOINT", "TENANT", "USER", "CALLER", "RESOURCE"),
          "MODEL",
          Set.of(
              "MODEL_REQUEST",
              "MODEL_TOKEN",
              "MODEL_COST",
              "MODEL_CONCURRENCY",
              "TENANT",
              "USER",
              "CALLER",
              "API_KEY",
              "CUSTOM_KEY"));
  private static final Map<String, Set<String>> MODE_PROTOCOLS =
      Map.of(
          "QPS",
          Set.of("HTTP", "GRPC", "MESSAGE", "MODEL", "ANY"),
          "CONCURRENCY",
          Set.of("HTTP", "GRPC", "TCP", "MODEL", "ANY"),
          "HEADER",
          Set.of("HTTP", "GRPC", "ANY"),
          "HOT_KEY",
          Set.of("HTTP", "GRPC", "MESSAGE", "MODEL", "ANY"),
          "CUSTOM",
          TRAFFIC_PROTOCOLS,
          "QUOTA",
          TRAFFIC_PROTOCOLS,
          "BANDWIDTH",
          Set.of("HTTP", "GRPC", "TCP", "ANY"),
          "CONNECTION",
          Set.of("HTTP", "GRPC", "TCP", "ANY"),
          "MODEL",
          Set.of("HTTP", "MODEL", "ANY"));

  private RateLimitRuleModelSupport() {}

  /** 校验企业级限流规则模型。 */
  public static ValidationResult validate(RateLimitRuleModel model) {
    List<String> errors = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    String limitMode = normalize(model.limitMode());
    String limitType = normalize(model.limitType());
    String limitAlgorithm = normalize(model.limitAlgorithm());
    String trafficProtocol = normalizeWithDefault(model.trafficProtocol(), "ANY");
    String coordinationMode =
        normalizeWithDefault(
            model.coordinationMode(), RateLimitRuleModeSupport.COORDINATION_MODE_LOCAL_ONLY);

    validateAllowed(limitMode, LIMIT_MODES, "limitMode不支持的企业级限流模式", errors);
    validateAllowed(limitType, LIMIT_TYPES, "limitType不支持的限流对象类型", errors);
    validateAllowed(limitAlgorithm, LIMIT_ALGORITHMS, "limitAlgorithm不支持的限流算法", errors);
    validateAllowed(trafficProtocol, TRAFFIC_PROTOCOLS, "trafficProtocol不支持的流量协议", errors);
    validateModeCompatibility(limitMode, limitType, limitAlgorithm, trafficProtocol, errors);
    validateOptionalKeyExtractor(model.keyExtractor(), errors);
    validateModeConfig(model, limitMode, trafficProtocol, coordinationMode, errors);
    return new ValidationResult(List.copyOf(errors), List.copyOf(warnings));
  }

  private static void validateModeCompatibility(
      String limitMode,
      String limitType,
      String limitAlgorithm,
      String trafficProtocol,
      List<String> errors) {
    if (!LIMIT_MODES.contains(limitMode)) {
      return;
    }
    validateAllowed(
        limitAlgorithm,
        MODE_ALGORITHMS.getOrDefault(limitMode, Set.of()),
        "limitMode=" + limitMode + "不支持当前limitAlgorithm",
        errors);
    validateAllowed(
        limitType,
        MODE_TYPES.getOrDefault(limitMode, Set.of()),
        "limitMode=" + limitMode + "不支持当前limitType",
        errors);
    validateAllowed(
        trafficProtocol,
        MODE_PROTOCOLS.getOrDefault(limitMode, Set.of()),
        "limitMode=" + limitMode + "不支持当前trafficProtocol",
        errors);
  }

  private static void validateModeConfig(
      RateLimitRuleModel model,
      String limitMode,
      String trafficProtocol,
      String coordinationMode,
      List<String> errors) {
    if (!LIMIT_MODES.contains(limitMode)) {
      return;
    }
    if (isEmpty(model.targetSelector())) {
      errors.add("限流规则必须配置targetSelector");
    }
    switch (limitMode) {
      case "QPS" -> validateQpsConfig(model.quotaConfig(), model.windowConfig(), errors);
      case "CONCURRENCY" -> validateConcurrencyConfig(model.concurrencyConfig(), errors);
      case "HEADER" -> validateHeaderConfig(model.keyExtractor(), trafficProtocol, errors);
      case "HOT_KEY" -> validateHotspotConfig(model.hotspotConfig(), model.keyExtractor(), errors);
      case "CUSTOM" ->
          validateCustomConfig(model.customPolicy(), model.observabilityConfig(), errors);
      case "QUOTA" -> validateQuotaConfig(model.quotaConfig(), coordinationMode, errors);
      case "BANDWIDTH" -> validateBandwidthConfig(model.quotaConfig(), errors);
      case "CONNECTION" -> validateConnectionConfig(model.concurrencyConfig(), errors);
      case "MODEL" -> validateModelConfig(model.modelLimitConfig(), errors);
      default -> {
        // Unsupported modes are already reported by enum validation.
      }
    }
  }

  private static void validateQpsConfig(
      Map<String, Object> quotaConfig, Map<String, Object> windowConfig, List<String> errors) {
    requirePositiveNumber(quotaConfig, "limit", "QPS限流必须配置quotaConfig.limit且必须大于0", errors);
    requireText(quotaConfig, "unit", "QPS限流必须配置quotaConfig.unit", errors);
    requireText(quotaConfig, "period", "QPS限流必须配置quotaConfig.period", errors);
    requireText(windowConfig, "windowType", "QPS限流必须配置windowConfig.windowType", errors);
    requirePositiveNumber(
        windowConfig, "durationMillis", "QPS限流必须配置windowConfig.durationMillis且必须大于0", errors);
  }

  private static void validateConcurrencyConfig(
      Map<String, Object> concurrencyConfig, List<String> errors) {
    requirePositiveNumber(
        concurrencyConfig,
        "maxConcurrent",
        "CONCURRENCY限流必须配置concurrencyConfig.maxConcurrent且必须大于0",
        errors);
    requireNonNegativeNumber(
        concurrencyConfig, "queueLimit", "concurrencyConfig.queueLimit不能小于0", errors);
    requireNonNegativeNumber(
        concurrencyConfig,
        "queueTimeoutMillis",
        "concurrencyConfig.queueTimeoutMillis不能小于0",
        errors);
  }

  private static void validateHeaderConfig(
      Map<String, Object> keyExtractor, String trafficProtocol, List<String> errors) {
    List<Map<?, ?>> keys = readObjectList(keyExtractor, "keys");
    if (keys.isEmpty()) {
      errors.add("HEADER限流必须配置keyExtractor.keys");
      return;
    }
    boolean hasHeaderKey = false;
    boolean hasGrpcMetadataKey = false;
    for (Map<?, ?> key : keys) {
      String source = normalize(asString(key.get("source")));
      hasHeaderKey = hasHeaderKey || "HEADER".equals(source);
      hasGrpcMetadataKey = hasGrpcMetadataKey || "GRPC_METADATA".equals(source);
    }
    if ("HTTP".equals(trafficProtocol) && !hasHeaderKey) {
      errors.add("HTTP Header限流必须至少配置一个source=HEADER的keyExtractor.keys项");
    }
    if ("GRPC".equals(trafficProtocol) && !hasGrpcMetadataKey) {
      errors.add("gRPC Metadata限流必须至少配置一个source=GRPC_METADATA的keyExtractor.keys项");
    }
    if ("ANY".equals(trafficProtocol) && !hasHeaderKey && !hasGrpcMetadataKey) {
      errors.add("HEADER限流必须至少配置一个HEADER或GRPC_METADATA来源的keyExtractor.keys项");
    }
  }

  private static void validateHotspotConfig(
      Map<String, Object> hotspotConfig, Map<String, Object> keyExtractor, List<String> errors) {
    requirePositiveNumber(hotspotConfig, "topN", "HOT_KEY限流必须配置hotspotConfig.topN且必须大于0", errors);
    requirePositiveNumber(
        hotspotConfig, "threshold", "HOT_KEY限流必须配置hotspotConfig.threshold且必须大于0", errors);
    if (readObjectList(keyExtractor, "keys").isEmpty()) {
      errors.add("HOT_KEY限流必须配置keyExtractor.keys用于识别热点key");
    }
  }

  private static void validateCustomConfig(
      Map<String, Object> customPolicy,
      Map<String, Object> observabilityConfig,
      List<String> errors) {
    String policyType = normalize(asString(read(customPolicy, "policyType")));
    validateAllowed(
        policyType, CUSTOM_POLICY_TYPES, "CUSTOM限流不支持当前customPolicy.policyType", errors);
    if (!CUSTOM_POLICY_TYPES.contains(policyType)) {
      return;
    }
    switch (policyType) {
      case "EXPRESSION" ->
          requireText(
              customPolicy, "expression", "EXPRESSION自定义限流必须配置customPolicy.expression", errors);
      case "SCRIPT" ->
          requireAnyText(
              customPolicy,
              Set.of("script", "expression"),
              "SCRIPT自定义限流必须配置script或expression",
              errors);
      case "PLUGIN" ->
          requireText(customPolicy, "pluginName", "PLUGIN自定义限流必须配置customPolicy.pluginName", errors);
      case "EXTERNAL_SERVICE" ->
          requireText(
              customPolicy, "endpoint", "EXTERNAL_SERVICE自定义限流必须配置customPolicy.endpoint", errors);
      default -> {
        // Unsupported policy types are already reported by enum validation.
      }
    }
    requirePositiveNumber(
        customPolicy, "timeoutMillis", "CUSTOM限流必须配置customPolicy.timeoutMillis且必须大于0", errors);
    requireText(customPolicy, "failPolicy", "CUSTOM限流必须配置customPolicy.failPolicy", errors);
    if (!hasNonEmptyList(observabilityConfig, "metricLabels")) {
      errors.add("CUSTOM限流必须配置observabilityConfig.metricLabels");
    }
  }

  private static void validateQuotaConfig(
      Map<String, Object> quotaConfig, String coordinationMode, List<String> errors) {
    requirePositiveNumber(quotaConfig, "limit", "QUOTA限流必须配置quotaConfig.limit且必须大于0", errors);
    if (!RateLimitRuleModeSupport.COORDINATION_MODE_GLOBAL_QUOTA.equals(coordinationMode)) {
      errors.add("QUOTA限流必须使用GLOBAL_QUOTA协调模式");
    }
  }

  private static void validateBandwidthConfig(
      Map<String, Object> quotaConfig, List<String> errors) {
    requirePositiveNumber(quotaConfig, "limit", "BANDWIDTH限流必须配置quotaConfig.limit且必须大于0", errors);
    String unit = normalize(asString(read(quotaConfig, "unit")));
    if (!"BYTE".equals(unit)) {
      errors.add("BANDWIDTH限流的quotaConfig.unit必须是BYTE");
    }
  }

  private static void validateConnectionConfig(
      Map<String, Object> concurrencyConfig, List<String> errors) {
    requirePositiveNumber(
        concurrencyConfig,
        "maxConcurrent",
        "CONNECTION限流必须配置concurrencyConfig.maxConcurrent且必须大于0",
        errors);
  }

  private static void validateModelConfig(
      Map<String, Object> modelLimitConfig, List<String> errors) {
    if (isEmpty(modelLimitConfig)) {
      errors.add("MODEL限流必须配置modelLimitConfig");
      return;
    }
    if (!hasPositiveNumber(
        modelLimitConfig, Set.of("requestLimit", "tokenLimit", "costLimit", "maxConcurrent"))) {
      errors.add("MODEL限流必须至少配置requestLimit、tokenLimit、costLimit或maxConcurrent之一且必须大于0");
    }
  }

  private static void validateOptionalKeyExtractor(
      Map<String, Object> keyExtractor, List<String> errors) {
    if (isEmpty(keyExtractor)) {
      return;
    }
    Object keysValue = keyExtractor.get("keys");
    if (!(keysValue instanceof List<?> keys)) {
      errors.add("keyExtractor.keys必须是数组");
      return;
    }
    for (Object keyItem : keys) {
      if (!(keyItem instanceof Map<?, ?> key)) {
        errors.add("keyExtractor.keys必须是对象数组");
        continue;
      }
      requireText(key, "name", "keyExtractor.keys[].name不能为空", errors);
      requireText(key, "key", "keyExtractor.keys[].key不能为空", errors);
      String source = normalize(asString(key.get("source")));
      validateAllowed(source, KEY_EXTRACTOR_SOURCES, "keyExtractor.keys[].source不支持", errors);
    }
  }

  private static void validateAllowed(
      String value, Set<String> allowedValues, String message, List<String> errors) {
    if (value == null || !allowedValues.contains(value)) {
      errors.add(message + ": " + value);
    }
  }

  private static boolean isEmpty(Map<String, Object> value) {
    return value == null || value.isEmpty();
  }

  private static Object read(Map<?, ?> map, String fieldName) {
    return map == null ? null : map.get(fieldName);
  }

  private static boolean hasNonEmptyList(Map<String, Object> map, String fieldName) {
    return read(map, fieldName) instanceof List<?> list && !list.isEmpty();
  }

  private static List<Map<?, ?>> readObjectList(Map<String, Object> map, String fieldName) {
    if (!(read(map, fieldName) instanceof List<?> values)) {
      return List.of();
    }
    List<Map<?, ?>> result = new ArrayList<>();
    for (Object value : values) {
      if (value instanceof Map<?, ?> item) {
        result.add(item);
      }
    }
    return result;
  }

  private static void requireText(
      Map<?, ?> map, String fieldName, String message, List<String> errors) {
    if (!hasText(read(map, fieldName))) {
      errors.add(message);
    }
  }

  private static void requireAnyText(
      Map<String, Object> map, Set<String> fieldNames, String message, List<String> errors) {
    for (String fieldName : fieldNames) {
      if (hasText(read(map, fieldName))) {
        return;
      }
    }
    errors.add(message);
  }

  private static void requirePositiveNumber(
      Map<String, Object> map, String fieldName, String message, List<String> errors) {
    if (!isPositiveNumber(read(map, fieldName))) {
      errors.add(message);
    }
  }

  private static void requireNonNegativeNumber(
      Map<String, Object> map, String fieldName, String message, List<String> errors) {
    Object value = read(map, fieldName);
    if (value == null) {
      return;
    }
    if (!isNonNegativeNumber(value)) {
      errors.add(message);
    }
  }

  private static boolean hasPositiveNumber(Map<String, Object> map, Set<String> fieldNames) {
    for (String fieldName : fieldNames) {
      if (isPositiveNumber(read(map, fieldName))) {
        return true;
      }
    }
    return false;
  }

  private static boolean isPositiveNumber(Object value) {
    return numericValue(value) > 0;
  }

  private static boolean isNonNegativeNumber(Object value) {
    return numericValue(value) >= 0;
  }

  private static double numericValue(Object value) {
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    if (value instanceof String text) {
      try {
        return Double.parseDouble(text);
      } catch (NumberFormatException exception) {
        return Double.NaN;
      }
    }
    return Double.NaN;
  }

  private static boolean hasText(Object value) {
    return value instanceof String text && !text.isBlank();
  }

  private static String asString(Object value) {
    return value == null ? null : value.toString();
  }

  private static String normalizeWithDefault(String value, String fallback) {
    String normalized = normalize(value);
    return normalized == null ? fallback : normalized;
  }

  private static String normalize(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim().toUpperCase(Locale.ROOT);
  }

  public record RateLimitRuleModel(
      String limitMode,
      String limitType,
      String limitAlgorithm,
      String trafficProtocol,
      String coordinationMode,
      Map<String, Object> targetSelector,
      Map<String, Object> requestMatcher,
      Map<String, Object> keyExtractor,
      List<Object> dimensions,
      Map<String, Object> quotaConfig,
      Map<String, Object> windowConfig,
      Map<String, Object> burstConfig,
      Map<String, Object> concurrencyConfig,
      Map<String, Object> hotspotConfig,
      Map<String, Object> customPolicy,
      Map<String, Object> modelLimitConfig,
      Map<String, Object> fallbackPolicy,
      Map<String, Object> responsePolicy,
      Map<String, Object> observabilityConfig,
      Map<String, Object> shadowConfig) {}

  public record ValidationResult(List<String> errors, List<String> warnings) {

    /** 判断校验结果是否通过。 */
    public boolean passed() {
      return errors.isEmpty();
    }
  }
}
