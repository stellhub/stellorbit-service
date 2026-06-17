package io.github.stellorbit.application.port;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.stellorbit.api.error.InvalidRuleRequestException;
import io.github.stellorbit.infrastructure.persistence.entity.ApplicationEntity;
import io.github.stellorbit.infrastructure.persistence.entity.GovernanceRuleEntity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class GovernanceRuleAggregatePayloadBuilder {

  private static final String AGGREGATE_SCHEMA_VERSION = "stellorbit.governance.aggregate.v1";
  private static final List<RuleTypeDescriptor> RULE_TYPES =
      List.of(
          new RuleTypeDescriptor("ROUTE", "ROUTE", "ROUTE_RULES", "routes", "RouteRuleSet"),
          new RuleTypeDescriptor(
              "BREAKER", "CIRCUIT_BREAKER", "BREAKER_RULES", "breaker", "CircuitBreakerRuleSet"),
          new RuleTypeDescriptor(
              "RATE_LIMIT", "RATE_LIMIT", "RATE_LIMIT_RULES", "limit", "RateLimitRuleSet"),
          new RuleTypeDescriptor("AUTH", "AUTH", "AUTH_RULES", "auth", "AuthRuleSet"));

  private final ObjectMapper objectMapper;

  public GovernanceRuleAggregatePayloadBuilder(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** 构建按治理规则类型聚合的stellnula发布配置。 */
  public List<AggregatedGovernanceRuleConfig> build(
      ApplicationEntity application,
      Long releaseVersion,
      String releaseName,
      String runtimeFormat,
      OffsetDateTime generatedAt,
      List<CompiledGovernanceRule> compiledRules) {
    Map<String, List<CompiledGovernanceRule>> groupedRules = groupedRules(compiledRules);
    List<AggregatedGovernanceRuleConfig> configs = new ArrayList<>();
    for (RuleTypeDescriptor descriptor : RULE_TYPES) {
      List<CompiledGovernanceRule> rules =
          groupedRules.getOrDefault(descriptor.ruleType(), List.of());
      configs.add(
          buildConfig(
              application,
              releaseVersion,
              releaseName,
              runtimeFormat,
              generatedAt,
              rules,
              descriptor));
    }
    return configs;
  }

  private AggregatedGovernanceRuleConfig buildConfig(
      ApplicationEntity application,
      Long releaseVersion,
      String releaseName,
      String runtimeFormat,
      OffsetDateTime generatedAt,
      List<CompiledGovernanceRule> rules,
      RuleTypeDescriptor descriptor) {
    String configId = aggregateConfigId(application, descriptor.stellnulaRuleType());
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("schemaVersion", AGGREGATE_SCHEMA_VERSION);
    payload.put("releaseVersion", releaseVersion);
    payload.put("generatedAt", generatedAt.toString());
    payload.put("applicationCode", application.getApplicationCode());
    payload.put("configId", configId);
    payload.put("ruleType", descriptor.stellnulaRuleType());
    payload.put("sourceRuleType", descriptor.ruleType());
    payload.put("targetService", application.getApplicationCode());
    payload.put("status", rules.isEmpty() ? "DISABLED" : "ACTIVE");
    payload.put("priority", aggregatePriority(rules));
    payload.put("releaseName", releaseName);
    payload.put("runtimeFormat", runtimeFormat);
    payload.put("ruleCount", rules.size());
    payload.put("rules", rules.stream().map(this::rulePayload).toList());
    payload.put(
        descriptor.validatorField(), validatorFieldPayload(rules, descriptor.validatorField()));

    String aggregateChecksum = sha256(toJson(payload));
    payload.put("checksum", aggregateChecksum);
    String content = toJson(payload);
    return new AggregatedGovernanceRuleConfig(
        configId,
        descriptor.ruleName(),
        descriptor.ruleType(),
        descriptor.stellnulaRuleType(),
        descriptor.publishKind(),
        content,
        sha256(content),
        aggregateChecksum,
        payload,
        rules);
  }

  private Map<String, List<CompiledGovernanceRule>> groupedRules(
      List<CompiledGovernanceRule> compiledRules) {
    Map<String, List<CompiledGovernanceRule>> grouped = new LinkedHashMap<>();
    for (CompiledGovernanceRule rule : compiledRules) {
      grouped.computeIfAbsent(rule.rule().getRuleType(), ignored -> new ArrayList<>()).add(rule);
    }
    return grouped;
  }

  private Map<String, Object> rulePayload(CompiledGovernanceRule compiledRule) {
    GovernanceRuleEntity rule = compiledRule.rule();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("ruleId", rule.getId().toString());
    payload.put("configId", compiledRule.configId());
    payload.put("ruleType", rule.getRuleType());
    payload.put("stellnulaRuleType", compiledRule.stellnulaRuleType());
    payload.put("ruleCode", rule.getRuleCode());
    payload.put("ruleName", rule.getRuleName());
    payload.put("targetService", compiledRule.targetService());
    payload.put("status", compiledRule.status());
    payload.put("priority", rule.getPriority());
    payload.put("draftVersion", rule.getDraftVersion());
    payload.put("schemaVersion", compiledRule.schemaVersion());
    payload.put("checksum", compiledRule.checksum());
    payload.put("content", compiledRule.contentModel());
    return payload;
  }

  private List<Object> validatorFieldPayload(
      List<CompiledGovernanceRule> rules, String validatorField) {
    return rules.stream()
        .map(rule -> rule.contentModel().get(validatorField))
        .filter(Objects::nonNull)
        .toList();
  }

  private int aggregatePriority(List<CompiledGovernanceRule> rules) {
    return rules.stream()
        .map(rule -> rule.rule().getPriority())
        .filter(Objects::nonNull)
        .min(Integer::compareTo)
        .orElse(1000);
  }

  private String aggregateConfigId(ApplicationEntity application, String stellnulaRuleType) {
    return "stellorbit."
        + normalize(application.getApplicationCode())
        + "."
        + normalize(stellnulaRuleType);
  }

  private String normalize(String value) {
    return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "-");
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new InvalidRuleRequestException("治理规则聚合内容序列化失败: " + exception.getMessage());
    }
  }

  private String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hashed);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256算法不可用", exception);
    }
  }

  private record RuleTypeDescriptor(
      String ruleType,
      String stellnulaRuleType,
      String publishKind,
      String validatorField,
      String ruleName) {}
}
