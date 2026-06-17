package io.github.stellorbit.application.port;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class GovernanceRuleConflictDetector {

  /** 检测同一发布批次内的跨规则冲突。 */
  public RuleSemanticCheckResult detect(List<CompiledGovernanceRule> compiledRules) {
    List<String> errors = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    Map<String, CompiledGovernanceRule> fingerprints = new LinkedHashMap<>();
    for (CompiledGovernanceRule rule : compiledRules) {
      String fingerprint = conflictFingerprint(rule);
      CompiledGovernanceRule sameFingerprint = fingerprints.putIfAbsent(fingerprint, rule);
      if (sameFingerprint != null) {
        errors.add(
            "规则作用域冲突: "
                + sameFingerprint.rule().getRuleCode()
                + " 与 "
                + rule.rule().getRuleCode()
                + " 在同一目标、优先级和匹配条件下同时生效");
      }
    }
    return new RuleSemanticCheckResult(errors, warnings);
  }

  private String conflictFingerprint(CompiledGovernanceRule rule) {
    Map<String, Object> model = rule.contentModel();
    return rule.rule().getRuleType()
        + "|"
        + rule.targetService()
        + "|"
        + rule.priority()
        + "|"
        + Objects.toString(detailFingerprint(rule.rule().getRuleType(), model), "");
  }

  @SuppressWarnings("unchecked")
  private Object detailFingerprint(String ruleType, Map<String, Object> model) {
    return switch (ruleType) {
      case "ROUTE" -> {
        Map<String, Object> route = (Map<String, Object>) model.getOrDefault("route", Map.of());
        yield List.of(
            route.get("protocol"),
            route.get("trafficDirection"),
            route.get("hosts"),
            route.get("matchConditions"));
      }
      case "RATE_LIMIT" -> {
        Map<String, Object> limit = (Map<String, Object>) model.getOrDefault("limit", Map.of());
        yield List.of(
            limit.get("enforcementMode"),
            limit.get("targetSelector"),
            limit.get("dimensions"),
            limit.get("windowConfig"));
      }
      case "BREAKER" -> {
        Map<String, Object> breaker = (Map<String, Object>) model.getOrDefault("breaker", Map.of());
        yield List.of(
            breaker.get("breakerType"), breaker.get("protocol"), breaker.get("targetSelector"));
      }
      case "AUTH" -> {
        Map<String, Object> auth = (Map<String, Object>) model.getOrDefault("auth", Map.of());
        yield List.of(
            auth.get("authPolicyType"),
            auth.get("authAction"),
            auth.get("workloadSelector"),
            auth.get("authorizationFrom"),
            auth.get("authorizationTo"));
      }
      default -> model;
    };
  }
}
