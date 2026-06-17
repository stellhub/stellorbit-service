package io.github.stellorbit.application.usecase;

import io.github.stellorbit.application.port.CompiledGovernanceRule;
import io.github.stellorbit.application.port.GovernanceRuleContentCompiler;
import io.github.stellorbit.infrastructure.persistence.entity.ApplicationEntity;
import io.github.stellorbit.infrastructure.persistence.entity.AuthRuleCertificateEntity;
import io.github.stellorbit.infrastructure.persistence.entity.GovernanceRuleEntity;
import io.github.stellorbit.infrastructure.persistence.entity.MtlsCertificateEntity;
import io.github.stellorbit.infrastructure.persistence.entity.RateLimitRuleEntity;
import io.github.stellorbit.infrastructure.persistence.entity.RuleValidationEntity;
import io.github.stellorbit.infrastructure.persistence.repository.ApplicationRepository;
import io.github.stellorbit.infrastructure.persistence.repository.AuthPolicyRuleRepository;
import io.github.stellorbit.infrastructure.persistence.repository.AuthRuleCertificateRepository;
import io.github.stellorbit.infrastructure.persistence.repository.BreakerRuleRepository;
import io.github.stellorbit.infrastructure.persistence.repository.GovernanceRuleRepository;
import io.github.stellorbit.infrastructure.persistence.repository.MtlsCertificateRepository;
import io.github.stellorbit.infrastructure.persistence.repository.RateLimitRuleRepository;
import io.github.stellorbit.infrastructure.persistence.repository.RouteRuleRepository;
import io.github.stellorbit.infrastructure.persistence.repository.RuleValidationRepository;
import io.github.stellorbit.api.dto.RuleValidationResponse;
import io.github.stellorbit.api.error.ResourceNotFoundException;
import io.github.stellorbit.api.security.ControlPlaneSecurityContextHolder;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ValidateGovernanceRuleUseCase {

  private static final Set<String> RULE_TYPES = Set.of("ROUTE", "BREAKER", "RATE_LIMIT", "AUTH");

  private final GovernanceRuleRepository governanceRuleRepository;
  private final ApplicationRepository applicationRepository;
  private final RouteRuleRepository routeRuleRepository;
  private final BreakerRuleRepository breakerRuleRepository;
  private final RateLimitRuleRepository rateLimitRuleRepository;
  private final AuthPolicyRuleRepository authPolicyRuleRepository;
  private final AuthRuleCertificateRepository authRuleCertificateRepository;
  private final MtlsCertificateRepository mtlsCertificateRepository;
  private final RuleValidationRepository ruleValidationRepository;
  private final GovernanceRuleContentCompiler governanceRuleContentCompiler;

  public ValidateGovernanceRuleUseCase(
      GovernanceRuleRepository governanceRuleRepository,
      ApplicationRepository applicationRepository,
      RouteRuleRepository routeRuleRepository,
      BreakerRuleRepository breakerRuleRepository,
      RateLimitRuleRepository rateLimitRuleRepository,
      AuthPolicyRuleRepository authPolicyRuleRepository,
      AuthRuleCertificateRepository authRuleCertificateRepository,
      MtlsCertificateRepository mtlsCertificateRepository,
      RuleValidationRepository ruleValidationRepository,
      GovernanceRuleContentCompiler governanceRuleContentCompiler) {
    this.governanceRuleRepository = governanceRuleRepository;
    this.applicationRepository = applicationRepository;
    this.routeRuleRepository = routeRuleRepository;
    this.breakerRuleRepository = breakerRuleRepository;
    this.rateLimitRuleRepository = rateLimitRuleRepository;
    this.authPolicyRuleRepository = authPolicyRuleRepository;
    this.authRuleCertificateRepository = authRuleCertificateRepository;
    this.mtlsCertificateRepository = mtlsCertificateRepository;
    this.ruleValidationRepository = ruleValidationRepository;
    this.governanceRuleContentCompiler = governanceRuleContentCompiler;
  }

  /** 执行发布前结构化校验。 */
  @Transactional
  public RuleValidationResponse validate(UUID ruleId, String validatedBy) {
    GovernanceRuleEntity rule =
        governanceRuleRepository
            .findById(ruleId)
            .orElseThrow(() -> new ResourceNotFoundException("GovernanceRule", ruleId));
    ControlPlaneSecurityContextHolder.requireInstanceSpace(rule.getInstanceSpaceId());

    List<String> errors = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    validateCommonRule(rule, errors, warnings);
    validateDetailRule(rule, errors, warnings);
    Map<String, Object> normalizedSnapshot = toNormalizedSnapshot(rule);
    String schemaVersion = "stellorbit.governance.v1";
    if (errors.isEmpty()) {
      try {
        ApplicationEntity application =
            applicationRepository
                .findById(rule.getApplicationId())
                .orElseThrow(
                    () -> new ResourceNotFoundException("Application", rule.getApplicationId()));
        CompiledGovernanceRule compiledRule =
            governanceRuleContentCompiler.compile(rule, application);
        normalizedSnapshot = compiledRule.contentModel();
        schemaVersion = compiledRule.schemaVersion();
        normalizedSnapshot.put("configId", compiledRule.configId());
        normalizedSnapshot.put("checksum", compiledRule.checksum());
        normalizedSnapshot.put("explain", compiledRule.explain());
        warnings.addAll(compiledRule.warnings());
      } catch (RuntimeException exception) {
        errors.add("CUE编译失败: " + exception.getMessage());
      }
    }

    RuleValidationEntity validation = new RuleValidationEntity();
    validation.setRuleId(rule.getId());
    validation.setDraftVersion(rule.getDraftVersion());
    validation.setSchemaVersion(schemaVersion);
    validation.setProtocolVersion("stellorbit.runtime.protocol.v1");
    validation.setCompatibilityStatus(errors.isEmpty() ? "COMPATIBLE" : "UNKNOWN");
    validation.setCompatibilityMessages(new ArrayList<>(warnings));
    validation.setSourceFormat("CUE");
    validation.setValidationStatus(errors.isEmpty() ? "PASSED" : "FAILED");
    validation.setNormalizedSnapshotJson(normalizedSnapshot);
    validation.setErrorMessages(errors);
    validation.setWarningMessages(warnings);
    validation.setValidatedBy(validatedBy);
    return toResponse(ruleValidationRepository.save(validation));
  }

  private void validateCommonRule(
      GovernanceRuleEntity rule, List<String> errors, List<String> warnings) {
    require(rule.getInstanceSpaceId(), "实例空间ID不能为空", errors);
    require(rule.getApplicationId(), "应用ID不能为空", errors);
    requireText(rule.getRuleCode(), "规则编码不能为空", errors);
    requireText(rule.getRuleName(), "规则名称不能为空", errors);
    requireText(rule.getCueSource(), "CUE规则原文不能为空", errors);
    requireText(rule.getCreatedBy(), "创建人不能为空", errors);
    requireText(rule.getUpdatedBy(), "更新人不能为空", errors);
    if (rule.getRuleType() == null || !RULE_TYPES.contains(rule.getRuleType())) {
      errors.add("规则类型不合法");
    }
    if (!"CUE".equals(rule.getSourceFormat())) {
      errors.add("sourceFormat必须是CUE");
    }
    if (rule.getRuntimeFormat() == null || !"JSON".equals(rule.getRuntimeFormat())) {
      errors.add("runtimeFormat只支持JSON");
    }
    if (rule.getPriority() == null || rule.getPriority() < 0) {
      errors.add("规则优先级不能小于0");
    }
    if (rule.getDraftVersion() == null || rule.getDraftVersion() <= 0) {
      errors.add("草稿版本必须大于0");
    }
    if (Boolean.FALSE.equals(rule.getEnabled())) {
      warnings.add("规则当前未启用，发布后客户端不会执行该规则");
    }
  }

  private void validateDetailRule(
      GovernanceRuleEntity rule, List<String> errors, List<String> warnings) {
    String ruleType = rule.getRuleType();
    if (ruleType == null || !RULE_TYPES.contains(ruleType)) {
      return;
    }
    switch (ruleType) {
      case "ROUTE" ->
          validateExists(routeRuleRepository.existsById(rule.getId()), "路由规则明细不存在", errors);
      case "BREAKER" ->
          validateExists(breakerRuleRepository.existsById(rule.getId()), "熔断规则明细不存在", errors);
      case "RATE_LIMIT" -> validateRateLimitRule(rule.getId(), errors, warnings);
      case "AUTH" -> validateAuthRule(rule.getId(), errors, warnings);
      default -> {
        // Invalid rule type is reported by common validation.
      }
    }
  }

  private void validateRateLimitRule(UUID ruleId, List<String> errors, List<String> warnings) {
    RateLimitRuleEntity detailRule = rateLimitRuleRepository.findById(ruleId).orElse(null);
    if (detailRule == null) {
      errors.add("限流规则明细不存在");
      return;
    }
    if ("GLOBAL_QUOTA".equals(detailRule.getEnforcementMode())) {
      warnings.add("GLOBAL_QUOTA运行时配额由外部分布式限流服务承载，本项目仅发布限流规则");
    }
  }

  private void validateAuthRule(UUID ruleId, List<String> errors, List<String> warnings) {
    if (!authPolicyRuleRepository.existsById(ruleId)) {
      errors.add("鉴权规则明细不存在");
      return;
    }

    List<AuthRuleCertificateEntity> bindings =
        authRuleCertificateRepository.findByAuthRuleId(ruleId);
    if (bindings.isEmpty()) {
      warnings.add("鉴权规则未绑定mTLS证书或JWKS材料");
      return;
    }
    for (AuthRuleCertificateEntity binding : bindings) {
      MtlsCertificateEntity certificate =
          mtlsCertificateRepository.findById(binding.getCertificateId()).orElse(null);
      if (certificate == null) {
        errors.add("绑定的证书不存在: " + binding.getCertificateId());
        continue;
      }
      validateCertificate(certificate, errors);
    }
  }

  private void validateCertificate(MtlsCertificateEntity certificate, List<String> errors) {
    if (!"ACTIVE".equals(certificate.getStatus())) {
      errors.add("证书不是ACTIVE状态: " + certificate.getId());
    }
    OffsetDateTime now = OffsetDateTime.now();
    if (certificate.getNotBefore() != null && certificate.getNotBefore().isAfter(now)) {
      errors.add("证书尚未生效: " + certificate.getId());
    }
    if (certificate.getNotAfter() != null && !certificate.getNotAfter().isAfter(now)) {
      errors.add("证书已过期: " + certificate.getId());
    }
    if (certificate.getNotBefore() != null
        && certificate.getNotAfter() != null
        && !certificate.getNotAfter().isAfter(certificate.getNotBefore())) {
      errors.add("证书有效期不合法: " + certificate.getId());
    }
  }

  private void require(Object value, String message, List<String> errors) {
    if (value == null) {
      errors.add(message);
    }
  }

  private void requireText(String value, String message, List<String> errors) {
    if (value == null || value.isBlank()) {
      errors.add(message);
    }
  }

  private void validateExists(boolean exists, String message, List<String> errors) {
    if (!exists) {
      errors.add(message);
    }
  }

  private Map<String, Object> toNormalizedSnapshot(GovernanceRuleEntity rule) {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("ruleId", rule.getId());
    snapshot.put("ruleType", rule.getRuleType());
    snapshot.put("ruleCode", rule.getRuleCode());
    snapshot.put("ruleName", rule.getRuleName());
    snapshot.put("draftVersion", rule.getDraftVersion());
    snapshot.put("sourceFormat", rule.getSourceFormat());
    snapshot.put("runtimeFormat", rule.getRuntimeFormat());
    return snapshot;
  }

  private RuleValidationResponse toResponse(RuleValidationEntity entity) {
    return new RuleValidationResponse(
        entity.getId(),
        entity.getRuleId(),
        entity.getDraftVersion(),
        entity.getSourceFormat(),
        entity.getValidationStatus(),
        entity.getNormalizedSnapshotJson(),
        entity.getErrorMessages(),
        entity.getWarningMessages(),
        entity.getValidatedBy(),
        entity.getValidatedAt());
  }
}
