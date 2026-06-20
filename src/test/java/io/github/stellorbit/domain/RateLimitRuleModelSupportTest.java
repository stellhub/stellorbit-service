package io.github.stellorbit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stellorbit.domain.RateLimitRuleModelSupport.RateLimitRuleModel;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RateLimitRuleModelSupportTest {

  @Test
  void shouldAcceptSupportedEnterpriseRateLimitModes() {
    List<RateLimitRuleModel> models =
        List.of(
            validModel("QPS", "REQUEST", "TOKEN_BUCKET", "HTTP", "LOCAL_ONLY"),
            validModel("CONCURRENCY", "CONNECTION", "CONCURRENCY_LIMIT", "HTTP", "LOCAL_ONLY"),
            validModel("HEADER", "HEADER", "SLIDING_WINDOW", "GRPC", "GLOBAL_SYNC"),
            validModel("HOT_KEY", "RESOURCE", "HOT_KEY", "HTTP", "LOCAL_ONLY"),
            validModel("CUSTOM", "CUSTOM_KEY", "CUSTOM", "ANY", "LOCAL_ONLY"),
            validModel("QUOTA", "TENANT", "QUOTA_LEASE", "ANY", "GLOBAL_QUOTA"),
            validModel("BANDWIDTH", "BYTE", "LEAKY_BUCKET", "TCP", "LOCAL_ONLY"),
            validModel("CONNECTION", "CONNECTION", "CONCURRENCY_LIMIT", "TCP", "LOCAL_ONLY"),
            validModel("MODEL", "MODEL_TOKEN", "SLIDING_WINDOW", "MODEL", "LOCAL_ONLY"));

    for (RateLimitRuleModel model : models) {
      assertThat(RateLimitRuleModelSupport.validate(model).errors()).isEmpty();
    }
  }

  @Test
  void shouldRejectUnsupportedModeAlgorithmCombination() {
    RateLimitRuleModel model =
        validModel("CONCURRENCY", "CONNECTION", "TOKEN_BUCKET", "HTTP", "LOCAL_ONLY");

    assertThat(RateLimitRuleModelSupport.validate(model).errors())
        .anyMatch(error -> error.contains("limitMode=CONCURRENCY不支持当前limitAlgorithm"));
  }

  @Test
  void shouldRejectGrpcHeaderLimitWithoutGrpcMetadataKey() {
    RateLimitRuleModel model =
        withKeyExtractor(
            validModel("HEADER", "HEADER", "SLIDING_WINDOW", "GRPC", "GLOBAL_SYNC"),
            Map.of(
                "strategy",
                "COMPOSITE",
                "keys",
                List.of(Map.of("name", "tenant", "source", "HEADER", "key", "x-tenant-id"))));

    assertThat(RateLimitRuleModelSupport.validate(model).errors())
        .contains("gRPC Metadata限流必须至少配置一个source=GRPC_METADATA的keyExtractor.keys项");
  }

  @Test
  void shouldRejectIncompleteCustomPolicy() {
    RateLimitRuleModel model =
        withCustomPolicy(
            withObservability(
                validModel("CUSTOM", "CUSTOM_KEY", "CUSTOM", "ANY", "LOCAL_ONLY"), Map.of()),
            Map.of("policyType", "EXPRESSION", "expression", "request.tenant"));

    assertThat(RateLimitRuleModelSupport.validate(model).errors())
        .contains(
            "CUSTOM限流必须配置customPolicy.timeoutMillis且必须大于0",
            "CUSTOM限流必须配置customPolicy.failPolicy",
            "CUSTOM限流必须配置observabilityConfig.metricLabels");
  }

  @Test
  void shouldRejectQuotaModeWithoutGlobalQuotaCoordination() {
    RateLimitRuleModel model = validModel("QUOTA", "TENANT", "QUOTA_LEASE", "ANY", "LOCAL_ONLY");

    assertThat(RateLimitRuleModelSupport.validate(model).errors())
        .contains("QUOTA限流必须使用GLOBAL_QUOTA协调模式");
  }

  private RateLimitRuleModel validModel(
      String limitMode,
      String limitType,
      String limitAlgorithm,
      String trafficProtocol,
      String coordinationMode) {
    return new RateLimitRuleModel(
        limitMode,
        limitType,
        limitAlgorithm,
        trafficProtocol,
        coordinationMode,
        Map.of("service", "order-service", "path", "/api/orders"),
        Map.of("http", Map.of("method", "GET", "pathPattern", "/api/orders/{id}")),
        Map.of(
            "strategy",
            "COMPOSITE",
            "keys",
            List.of(
                Map.of("name", "tenant", "source", "GRPC_METADATA", "key", "x-tenant-id"),
                Map.of("name", "api", "source", "GRPC_METHOD", "key", "method"))),
        List.of(Map.of("name", "tenant", "source", "GRPC_METADATA", "key", "x-tenant-id")),
        Map.of(
            "limit",
            1000,
            "unit",
            limitMode.equals("BANDWIDTH") ? "BYTE" : "REQUEST",
            "period",
            "SECOND"),
        Map.of("windowType", "SLIDING", "durationMillis", 60000),
        Map.of("capacity", 2000, "refillRate", 1000),
        Map.of("maxConcurrent", 100, "queueLimit", 1000, "queueTimeoutMillis", 200),
        Map.of("topN", 100, "threshold", 1000, "ttlMillis", 60000),
        Map.of(
            "policyType",
            "EXPRESSION",
            "expression",
            "request.tenant",
            "timeoutMillis",
            20,
            "failPolicy",
            "FAIL_OPEN"),
        Map.of("provider", "openai-compatible", "model", "demo", "tokenLimit", 10000),
        Map.of("mode", "FAIL_OPEN"),
        Map.of("httpStatus", 429),
        Map.of("metricLabels", List.of("tenant", "api")),
        Map.of("enabled", false));
  }

  private RateLimitRuleModel withKeyExtractor(
      RateLimitRuleModel model, Map<String, Object> keyExtractor) {
    return new RateLimitRuleModel(
        model.limitMode(),
        model.limitType(),
        model.limitAlgorithm(),
        model.trafficProtocol(),
        model.coordinationMode(),
        model.targetSelector(),
        model.requestMatcher(),
        keyExtractor,
        model.dimensions(),
        model.quotaConfig(),
        model.windowConfig(),
        model.burstConfig(),
        model.concurrencyConfig(),
        model.hotspotConfig(),
        model.customPolicy(),
        model.modelLimitConfig(),
        model.fallbackPolicy(),
        model.responsePolicy(),
        model.observabilityConfig(),
        model.shadowConfig());
  }

  private RateLimitRuleModel withCustomPolicy(
      RateLimitRuleModel model, Map<String, Object> customPolicy) {
    return new RateLimitRuleModel(
        model.limitMode(),
        model.limitType(),
        model.limitAlgorithm(),
        model.trafficProtocol(),
        model.coordinationMode(),
        model.targetSelector(),
        model.requestMatcher(),
        model.keyExtractor(),
        model.dimensions(),
        model.quotaConfig(),
        model.windowConfig(),
        model.burstConfig(),
        model.concurrencyConfig(),
        model.hotspotConfig(),
        customPolicy,
        model.modelLimitConfig(),
        model.fallbackPolicy(),
        model.responsePolicy(),
        model.observabilityConfig(),
        model.shadowConfig());
  }

  private RateLimitRuleModel withObservability(
      RateLimitRuleModel model, Map<String, Object> observabilityConfig) {
    return new RateLimitRuleModel(
        model.limitMode(),
        model.limitType(),
        model.limitAlgorithm(),
        model.trafficProtocol(),
        model.coordinationMode(),
        model.targetSelector(),
        model.requestMatcher(),
        model.keyExtractor(),
        model.dimensions(),
        model.quotaConfig(),
        model.windowConfig(),
        model.burstConfig(),
        model.concurrencyConfig(),
        model.hotspotConfig(),
        model.customPolicy(),
        model.modelLimitConfig(),
        model.fallbackPolicy(),
        model.responsePolicy(),
        observabilityConfig,
        model.shadowConfig());
  }
}
