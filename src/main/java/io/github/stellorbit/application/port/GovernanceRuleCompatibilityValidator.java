package io.github.stellorbit.application.port;

import io.github.stellorbit.infrastructure.persistence.entity.RuleReleaseEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class GovernanceRuleCompatibilityValidator {

  /** 校验新发布版本与上一发布快照之间的兼容性。 */
  public RuleSemanticCheckResult validate(
      Long nextReleaseVersion,
      List<CompiledGovernanceRule> compiledRules,
      RuleReleaseEntity previousRelease) {
    List<String> errors = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    if (previousRelease == null) {
      return new RuleSemanticCheckResult(errors, warnings);
    }
    if (nextReleaseVersion != null
        && previousRelease.getReleaseVersion() != null
        && nextReleaseVersion <= previousRelease.getReleaseVersion()) {
      errors.add("发布版本必须大于上一版本: " + previousRelease.getReleaseVersion());
    }
    Map<String, Map<String, Object>> previousRules = previousRules(previousRelease);
    Map<String, CompiledGovernanceRule> nextRules = new LinkedHashMap<>();
    for (CompiledGovernanceRule compiledRule : compiledRules) {
      nextRules.put(compiledRule.rule().getRuleCode(), compiledRule);
      Map<String, Object> previous = previousRules.get(compiledRule.rule().getRuleCode());
      if (previous == null) {
        continue;
      }
      validateRuleCompatibility(compiledRule, previous, errors);
    }
    for (String previousRuleCode : previousRules.keySet()) {
      if (!nextRules.containsKey(previousRuleCode)) {
        warnings.add("上一版本规则在本次发布中被移除: " + previousRuleCode);
      }
    }
    return new RuleSemanticCheckResult(errors, warnings);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Map<String, Object>> previousRules(RuleReleaseEntity previousRelease) {
    Map<String, Object> snapshot = previousRelease.getReleaseSnapshotJson();
    if (snapshot == null || !(snapshot.get("rules") instanceof List<?> rules)) {
      return Map.of();
    }
    Map<String, Map<String, Object>> indexed = new LinkedHashMap<>();
    for (Object item : rules) {
      if (item instanceof Map<?, ?> map) {
        Object ruleCode = map.get("ruleCode");
        if (ruleCode != null) {
          indexed.put(ruleCode.toString(), (Map<String, Object>) map);
        }
      }
    }
    return indexed;
  }

  private void validateRuleCompatibility(
      CompiledGovernanceRule next, Map<String, Object> previous, List<String> errors) {
    String previousRuleType = Objects.toString(previous.get("ruleType"), "");
    if (!previousRuleType.isBlank() && !previousRuleType.equals(next.rule().getRuleType())) {
      errors.add("规则类型不兼容: " + next.rule().getRuleCode());
    }
    String previousSchemaVersion = Objects.toString(previous.get("schemaVersion"), "");
    if (!previousSchemaVersion.isBlank()
        && !major(previousSchemaVersion).equals(major(next.schemaVersion()))) {
      errors.add(
          "CUE Schema主版本不兼容: "
              + next.rule().getRuleCode()
              + " 从 "
              + previousSchemaVersion
              + " 到 "
              + next.schemaVersion());
    }
  }

  private String major(String schemaVersion) {
    int index = schemaVersion.lastIndexOf(".v");
    if (index < 0) {
      return schemaVersion;
    }
    return schemaVersion.substring(0, index + 2);
  }
}
