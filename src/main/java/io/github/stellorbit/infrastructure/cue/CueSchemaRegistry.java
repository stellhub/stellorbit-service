package io.github.stellorbit.infrastructure.cue;

import io.github.stellorbit.api.error.InvalidRuleRequestException;
import io.github.stellorbit.infrastructure.persistence.entity.CueSchemaVersionEntity;
import io.github.stellorbit.infrastructure.persistence.repository.CueSchemaVersionRepository;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class CueSchemaRegistry {

  private static final String SCHEMA_VERSION = "stellorbit.governance.v1";

  private final CueSchemaVersionRepository cueSchemaVersionRepository;

  public CueSchemaRegistry(CueSchemaVersionRepository cueSchemaVersionRepository) {
    this.cueSchemaVersionRepository = cueSchemaVersionRepository;
  }

  /** 读取当前规则类型的CUE Schema版本。 */
  public CueSchemaDefinition currentSchema(String ruleType) {
    return cueSchemaVersionRepository
        .findFirstByRuleTypeAndStatusOrderByCreatedAtDesc(ruleType, "ACTIVE")
        .map(this::fromEntity)
        .orElseGet(() -> builtinSchema(ruleType));
  }

  private CueSchemaDefinition fromEntity(CueSchemaVersionEntity entity) {
    return new CueSchemaDefinition(
        entity.getRuleType(),
        entity.getSchemaVersion(),
        entity.getSchemaName(),
        entity.getCueSchema());
  }

  private CueSchemaDefinition builtinSchema(String ruleType) {
    return switch (ruleType) {
      case "ROUTE" ->
          new CueSchemaDefinition(
              ruleType, SCHEMA_VERSION, "builtin-route-v1", schema("ROUTE", routeBlock()));
      case "BREAKER" ->
          new CueSchemaDefinition(
              ruleType, SCHEMA_VERSION, "builtin-breaker-v1", schema("BREAKER", breakerBlock()));
      case "RATE_LIMIT" ->
          new CueSchemaDefinition(
              ruleType,
              SCHEMA_VERSION,
              "builtin-rate-limit-v1",
              schema("RATE_LIMIT", rateLimitBlock()));
      case "AUTH" ->
          new CueSchemaDefinition(
              ruleType, SCHEMA_VERSION, "builtin-auth-v1", schema("AUTH", authBlock()));
      default -> throw new InvalidRuleRequestException("不支持的CUE Schema规则类型: " + ruleType);
    };
  }

  private String schema(String ruleType, String detailBlock) {
    return """
    package stellorbit

    #NonEmptyString: string
    #Object: {...}
    #Array: [..._]
    #RateLimitMode: "QPS" | "CONCURRENCY" | "HEADER" | "HOT_KEY" | "CUSTOM" | "QUOTA" | "BANDWIDTH" | "CONNECTION" | "MODEL"
    #RateLimitType: "REQUEST" | "CONNECTION" | "BYTE" | "TENANT" | "USER" | "CALLER" | "API_KEY" | "RESOURCE" | "HEADER" | "GRPC_METADATA" | "IP" | "ENDPOINT" | "METHOD" | "TOPIC" | "MODEL_REQUEST" | "MODEL_TOKEN" | "MODEL_COST" | "MODEL_CONCURRENCY" | "CUSTOM_KEY"
    #RateLimitAlgorithm: "TOKEN_BUCKET" | "LEAKY_BUCKET" | "FIXED_WINDOW" | "SLIDING_WINDOW" | "QUOTA_LEASE" | "CONCURRENCY_LIMIT" | "HOT_KEY" | "CUSTOM" | "ADAPTIVE"
    #TrafficProtocol: "HTTP" | "GRPC" | "TCP" | "MESSAGE" | "MODEL" | "ANY"
    #KeyExtractorSource: "HEADER" | "GRPC_METADATA" | "QUERY" | "COOKIE" | "PATH" | "HTTP_METHOD" | "HTTP_PATH" | "GRPC_SERVICE" | "GRPC_METHOD" | "REMOTE_IP" | "JWT_CLAIM" | "API_KEY" | "TENANT" | "USER" | "CALLER" | "BODY_JSON_PATH" | "CUSTOM_EXPRESSION"
    #KeyExtractor: {
      strategy?: #NonEmptyString
      failOnMissing?: bool
      hash?: #Object
      keys?: [...{
        name: #NonEmptyString
        source: #KeyExtractorSource
        key: #NonEmptyString
        required?: bool
        normalize?: #NonEmptyString
      }]
      ...
    }
    #QuotaConfig: {
      limit?: number & >0
      unit?: #NonEmptyString
      period?: #NonEmptyString
      scope?: #NonEmptyString
      warmup?: #Object
      ...
    }
    #WindowConfig: {
      windowType?: #NonEmptyString
      durationMillis?: int & >0
      bucketCount?: int & >0
      precisionMillis?: int & >0
      refillSeconds?: int & >0
      ...
    }
    #ConcurrencyConfig: {
      maxConcurrent?: int & >0
      queueLimit?: int & >=0
      queueTimeoutMillis?: int & >=0
      adaptive?: bool
      releaseOn?: #NonEmptyString
      ...
    }
    #HotspotConfig: {
      enabled?: bool
      metric?: #NonEmptyString
      topN?: int & >0
      threshold?: number & >0
      ttlMillis?: int & >0
      adaptive?: bool
      isolation?: #Object
      degradePolicy?: #Object
      sharding?: #Object
      ...
    }
    #CustomPolicy: {
      policyType?: "EXPRESSION" | "SCRIPT" | "PLUGIN" | "EXTERNAL_SERVICE"
      language?: #NonEmptyString
      expression?: #NonEmptyString
      script?: #NonEmptyString
      pluginName?: #NonEmptyString
      pluginVersion?: #NonEmptyString
      endpoint?: #NonEmptyString
      timeoutMillis?: int & >0
      failPolicy?: #NonEmptyString
      parameters?: #Object
      ...
    }
    #ModelLimitConfig: {
      provider?: #NonEmptyString
      model?: #NonEmptyString
      requestLimit?: number & >0
      tokenLimit?: number & >0
      costLimit?: number & >0
      maxConcurrent?: int & >0
      contextLimit?: int & >0
      ...
    }

    #CommonRule: {
      schemaVersion: *"%s" | string
      ruleType: "%s"
      ruleCode: #NonEmptyString
      ruleName: #NonEmptyString
      targetService: #NonEmptyString
      status: *"ACTIVE" | "ACTIVE" | "DISABLED"
      sourceFormat: *"CUE" | "CUE"
      runtimeFormat: *"JSON" | "JSON"
      priority: *1000 | int & >=0
      draftVersion: int & >0
      enabled: *true | bool
      metadata?: #Object
    }

    #Rule: #CommonRule & {
    %s
    }

    rule: #Rule
    """
        .formatted(SCHEMA_VERSION, ruleType, indent(detailBlock));
  }

  private String routeBlock() {
    return """
    routes: {
      routeType: #NonEmptyString
      trafficDirection: *"EAST_WEST" | "EAST_WEST" | "NORTH_SOUTH" | "EGRESS"
      protocol: #NonEmptyString
      gateways: *[] | #Array
      hosts: [...#NonEmptyString]
      sourceSelector: *{} | #Object
      matchConditions: *[] | #Array
      destinations: [...#Object]
      routeAction: *{} | #Object
      rewritePolicy: *{} | #Object
      redirectPolicy: *{} | #Object
      mirrorPolicy: *{} | #Object
      faultInjectionPolicy: *{} | #Object
      timeoutPolicy: *{} | #Object
      retryPolicy: *{} | #Object
      loadBalancePolicy: *{} | #Object
      localityPolicy: *{} | #Object
    }
    """;
  }

  private String breakerBlock() {
    return """
    breaker: {
      breakerType: #NonEmptyString
      protocol: #NonEmptyString
      targetSelector: #Object
      windowType: *"COUNT_BASED" | "COUNT_BASED" | "TIME_BASED"
      windowSize: int & >0
      minimumCalls: int & >=0
      failureRateThreshold?: number & >=0 & <=100
      slowCallRateThreshold?: number & >=0 & <=100
      slowCallDurationMillis?: int & >=0
      openStateWaitMillis?: int & >=0
      permittedHalfOpenCalls?: int & >=0
      connectionPoolPolicy: *{} | #Object
      outlierDetectionPolicy: *{} | #Object
      retryBudgetPolicy: *{} | #Object
      exceptionRecordPolicy: *{} | #Object
      exceptionIgnorePolicy: *{} | #Object
      fallbackPolicy: *{} | #Object
    }
    """;
  }

  private String rateLimitBlock() {
    return """
    limit: {
      limitMode: *"QPS" | #RateLimitMode
      limitType: #RateLimitType
      limitAlgorithm: #RateLimitAlgorithm
      trafficProtocol: *"ANY" | #TrafficProtocol
      executionLocation: *"APPLICATION" | "APPLICATION" | "SIDECAR" | "GATEWAY" | "EDGE"
      coordinationMode: *"LOCAL_ONLY" | "LOCAL_ONLY" | "GLOBAL_SYNC" | "GLOBAL_QUOTA"
      enforcementMode?: "LOCAL" | "GLOBAL_SYNC" | "GLOBAL_QUOTA" | "EDGE"
      distributedCoordination: *false | bool
      targetSelector: #Object
      requestMatcher: *{} | #Object
      keyExtractor: #KeyExtractor
      dimensions: #Array
      quotaConfig: #QuotaConfig
      windowConfig: #WindowConfig
      burstConfig: *{} | #Object
      concurrencyConfig: #ConcurrencyConfig
      hotspotConfig: #HotspotConfig
      customPolicy: #CustomPolicy
      modelLimitConfig: #ModelLimitConfig
      fallbackPolicy: *{} | #Object
      responsePolicy: *{} | #Object
      observabilityConfig: *{} | #Object
      shadowConfig: *{} | #Object
    }
    """;
  }

  private String authBlock() {
    return """
    auth: {
      authPolicyType: #NonEmptyString
      authAction: *"ALLOW" | "ALLOW" | "DENY" | "AUDIT" | "CUSTOM"
      mtlsMode?: "STRICT" | "PERMISSIVE" | "DISABLE"
      trustDomain?: string
      workloadSelector: *{} | #Object
      peerSources: *[] | #Array
      requestAuthentications: *[] | #Array
      authorizationFrom: *[] | #Array
      authorizationTo: *[] | #Array
      authorizationWhen: *[] | #Array
      jwtRules: *[] | #Array
      extAuthzProvider: *{} | #Object
      auditPolicy: *{} | #Object
    }
    """;
  }

  private String indent(String value) {
    return value.lines().map(line -> "  " + line).reduce("", (left, right) -> left + right + "\n");
  }

  public Map<String, Object> schemaMetadata(CueSchemaDefinition definition) {
    return Map.of(
        "ruleType", definition.ruleType(),
        "schemaVersion", definition.schemaVersion(),
        "schemaName", definition.schemaName());
  }
}
