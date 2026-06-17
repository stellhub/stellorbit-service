package io.github.stellorbit.application.usecase;

import io.github.stellorbit.api.dto.PublishGovernanceRulesRequest;
import io.github.stellorbit.api.dto.RuleCompileDryRunResponse;
import io.github.stellorbit.api.dto.RuleReleaseDryRunResponse;
import io.github.stellorbit.api.error.InvalidRuleRequestException;
import io.github.stellorbit.api.error.ResourceNotFoundException;
import io.github.stellorbit.api.security.ControlPlaneSecurityContextHolder;
import io.github.stellorbit.application.port.AggregatedGovernanceRuleConfig;
import io.github.stellorbit.application.port.CompiledGovernanceRule;
import io.github.stellorbit.application.port.GovernanceRuleAggregatePayloadBuilder;
import io.github.stellorbit.application.port.GovernanceRuleCompatibilityValidator;
import io.github.stellorbit.application.port.GovernanceRuleConflictDetector;
import io.github.stellorbit.application.port.GovernanceRuleContentCompiler;
import io.github.stellorbit.application.port.RuleSemanticCheckResult;
import io.github.stellorbit.infrastructure.persistence.entity.ApplicationEntity;
import io.github.stellorbit.infrastructure.persistence.entity.GovernanceRuleEntity;
import io.github.stellorbit.infrastructure.persistence.entity.RuleReleaseEntity;
import io.github.stellorbit.infrastructure.persistence.repository.ApplicationRepository;
import io.github.stellorbit.infrastructure.persistence.repository.GovernanceRuleRepository;
import io.github.stellorbit.infrastructure.persistence.repository.RuleReleaseRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DryRunGovernanceRulesUseCase {

  private final GovernanceRuleRepository governanceRuleRepository;
  private final ApplicationRepository applicationRepository;
  private final RuleReleaseRepository ruleReleaseRepository;
  private final GovernanceRuleContentCompiler governanceRuleContentCompiler;
  private final GovernanceRuleAggregatePayloadBuilder governanceRuleAggregatePayloadBuilder;
  private final GovernanceRuleConflictDetector governanceRuleConflictDetector;
  private final GovernanceRuleCompatibilityValidator governanceRuleCompatibilityValidator;

  public DryRunGovernanceRulesUseCase(
      GovernanceRuleRepository governanceRuleRepository,
      ApplicationRepository applicationRepository,
      RuleReleaseRepository ruleReleaseRepository,
      GovernanceRuleContentCompiler governanceRuleContentCompiler,
      GovernanceRuleAggregatePayloadBuilder governanceRuleAggregatePayloadBuilder,
      GovernanceRuleConflictDetector governanceRuleConflictDetector,
      GovernanceRuleCompatibilityValidator governanceRuleCompatibilityValidator) {
    this.governanceRuleRepository = governanceRuleRepository;
    this.applicationRepository = applicationRepository;
    this.ruleReleaseRepository = ruleReleaseRepository;
    this.governanceRuleContentCompiler = governanceRuleContentCompiler;
    this.governanceRuleAggregatePayloadBuilder = governanceRuleAggregatePayloadBuilder;
    this.governanceRuleConflictDetector = governanceRuleConflictDetector;
    this.governanceRuleCompatibilityValidator = governanceRuleCompatibilityValidator;
  }

  /** 执行发布前dry-run编译和解释。 */
  @Transactional(readOnly = true)
  public RuleReleaseDryRunResponse dryRun(PublishGovernanceRulesRequest request) {
    ControlPlaneSecurityContextHolder.requireInstanceSpace(request.instanceSpaceId());
    String runtimeFormat = normalizeRuntimeFormat(request.runtimeFormat());
    ApplicationEntity application =
        applicationRepository
            .findById(request.applicationId())
            .orElseThrow(
                () -> new ResourceNotFoundException("Application", request.applicationId()));
    List<GovernanceRuleEntity> rules = findRules(request);
    if (rules.isEmpty()) {
      throw new InvalidRuleRequestException("没有可dry-run的启用规则");
    }

    List<String> errors = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    List<String> explain = new ArrayList<>();
    List<CompiledGovernanceRule> compiledRules = new ArrayList<>();
    List<RuleCompileDryRunResponse> ruleResponses = new ArrayList<>();
    for (GovernanceRuleEntity rule : rules) {
      try {
        CompiledGovernanceRule compiledRule =
            governanceRuleContentCompiler.compile(rule, application);
        compiledRules.add(compiledRule);
        ruleResponses.add(toRuleResponse(runtimeFormat, compiledRule, List.of()));
      } catch (RuntimeException exception) {
        errors.add("规则 " + rule.getRuleCode() + " 编译失败: " + exception.getMessage());
        ruleResponses.add(toFailedRuleResponse(runtimeFormat, rule, exception.getMessage()));
      }
    }

    if (!compiledRules.isEmpty()) {
      RuleSemanticCheckResult conflictResult = governanceRuleConflictDetector.detect(compiledRules);
      errors.addAll(conflictResult.errors());
      warnings.addAll(conflictResult.warnings());
      RuleReleaseEntity previousRelease =
          ruleReleaseRepository
              .findTopByInstanceSpaceIdAndApplicationIdOrderByReleaseVersionDesc(
                  request.instanceSpaceId(), request.applicationId())
              .orElse(null);
      RuleSemanticCheckResult compatibilityResult =
          governanceRuleCompatibilityValidator.validate(
              request.releaseVersion(), compiledRules, previousRelease);
      errors.addAll(compatibilityResult.errors());
      warnings.addAll(compatibilityResult.warnings());
    }

    OffsetDateTime generatedAt = OffsetDateTime.now();
    List<AggregatedGovernanceRuleConfig> aggregatedConfigs =
        governanceRuleAggregatePayloadBuilder.build(
            application,
            request.releaseVersion(),
            request.releaseName(),
            runtimeFormat,
            generatedAt,
            compiledRules);
    Map<String, Object> releaseSnapshot =
        buildReleaseSnapshot(
            request, runtimeFormat, application, compiledRules, generatedAt, aggregatedConfigs);
    explain.add(
        "dry-run only: no rule_releases, release_items or stellnula_publish_records created");
    explain.add("compiled rule count: " + compiledRules.size());
    explain.add("aggregated governance config count: " + aggregatedConfigs.size());
    return new RuleReleaseDryRunResponse(
        request.instanceSpaceId(),
        request.applicationId(),
        request.releaseVersion(),
        runtimeFormat,
        releaseSnapshot,
        ruleResponses,
        errors,
        warnings,
        explain);
  }

  private List<GovernanceRuleEntity> findRules(PublishGovernanceRulesRequest request) {
    if (request.ruleIds() == null || request.ruleIds().isEmpty()) {
      return governanceRuleRepository
          .findByInstanceSpaceIdAndApplicationIdAndEnabledTrueOrderByPriorityAscRuleCodeAsc(
              request.instanceSpaceId(), request.applicationId());
    }
    List<GovernanceRuleEntity> rules =
        governanceRuleRepository
            .findByInstanceSpaceIdAndApplicationIdAndIdInAndEnabledTrueOrderByPriorityAscRuleCodeAsc(
                request.instanceSpaceId(), request.applicationId(), request.ruleIds());
    long requestedCount = request.ruleIds().stream().distinct().count();
    if (requestedCount != rules.size()) {
      throw new InvalidRuleRequestException("部分规则不存在、不属于当前作用域或未启用");
    }
    return rules;
  }

  private Map<String, Object> buildReleaseSnapshot(
      PublishGovernanceRulesRequest request,
      String runtimeFormat,
      ApplicationEntity application,
      List<CompiledGovernanceRule> compiledRules,
      OffsetDateTime generatedAt,
      List<AggregatedGovernanceRuleConfig> aggregatedConfigs) {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("instanceSpaceId", request.instanceSpaceId().toString());
    snapshot.put("applicationId", request.applicationId().toString());
    snapshot.put("applicationCode", application.getApplicationCode());
    snapshot.put("releaseVersion", request.releaseVersion());
    snapshot.put("releaseName", request.releaseName());
    snapshot.put("generatedAt", generatedAt.toString());
    snapshot.put("sourceFormat", "CUE");
    snapshot.put("runtimeFormat", runtimeFormat);
    snapshot.put(
        "governanceConfigs",
        aggregatedConfigs.stream().map(this::toAggregateConfigSnapshot).toList());
    snapshot.put(
        "rules", compiledRules.stream().map(CompiledGovernanceRule::contentModel).toList());
    return snapshot;
  }

  private Map<String, Object> toAggregateConfigSnapshot(
      AggregatedGovernanceRuleConfig aggregatedConfig) {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("configId", aggregatedConfig.configId());
    snapshot.put("ruleName", aggregatedConfig.ruleName());
    snapshot.put("ruleType", aggregatedConfig.ruleType());
    snapshot.put("stellnulaRuleType", aggregatedConfig.stellnulaRuleType());
    snapshot.put("publishKind", aggregatedConfig.publishKind());
    snapshot.put("ruleCount", aggregatedConfig.rules().size());
    snapshot.put("checksum", aggregatedConfig.aggregateChecksum());
    snapshot.put("contentChecksum", aggregatedConfig.checksum());
    snapshot.put("content", aggregatedConfig.contentModel());
    return snapshot;
  }

  private RuleCompileDryRunResponse toRuleResponse(
      String runtimeFormat, CompiledGovernanceRule compiledRule, List<String> errors) {
    return new RuleCompileDryRunResponse(
        compiledRule.rule().getId(),
        compiledRule.rule().getRuleType(),
        compiledRule.rule().getRuleCode(),
        compiledRule.rule().getRuleName(),
        compiledRule.schemaVersion(),
        compiledRule.configId(),
        compiledRule.targetService(),
        runtimeFormat,
        compiledRule.checksum(),
        compiledRule.contentModel(),
        compiledRule.content(),
        errors,
        compiledRule.warnings(),
        compiledRule.explain());
  }

  private RuleCompileDryRunResponse toFailedRuleResponse(
      String runtimeFormat, GovernanceRuleEntity rule, String error) {
    return new RuleCompileDryRunResponse(
        rule.getId(),
        rule.getRuleType(),
        rule.getRuleCode(),
        rule.getRuleName(),
        null,
        null,
        null,
        runtimeFormat,
        null,
        Map.of(),
        null,
        List.of(error),
        List.of(),
        List.of("CUE compilation failed before normalized snapshot generation"));
  }

  private String normalizeRuntimeFormat(String runtimeFormat) {
    String normalized =
        runtimeFormat == null || runtimeFormat.isBlank()
            ? "JSON"
            : runtimeFormat.trim().toUpperCase();
    if (!"JSON".equals(normalized)) {
      throw new InvalidRuleRequestException("runtimeFormat只支持JSON");
    }
    return normalized;
  }
}
