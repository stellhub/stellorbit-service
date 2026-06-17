package io.github.stellorbit.infrastructure.cue;

import io.github.stellorbit.infrastructure.persistence.entity.CueSchemaVersionEntity;
import io.github.stellorbit.infrastructure.persistence.repository.CueSchemaVersionRepository;
import io.github.stellorbit.api.error.InvalidRuleRequestException;
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
      limitType: #NonEmptyString
      limitAlgorithm: #NonEmptyString
      enforcementMode: *"LOCAL" | "LOCAL" | "GLOBAL_SYNC" | "GLOBAL_QUOTA"
      targetSelector: #Object
      dimensions: [...#NonEmptyString]
      quotaConfig: #Object
      windowConfig: #Object
      burstConfig: *{} | #Object
      modelLimitConfig: *{} | #Object
      fallbackPolicy: *{} | #Object
      responsePolicy: *{} | #Object
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
