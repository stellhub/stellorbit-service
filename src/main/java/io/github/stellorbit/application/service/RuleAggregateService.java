package io.github.stellorbit.application.service;

import io.github.stellorbit.domain.Identifiable;
import io.github.stellorbit.infrastructure.persistence.entity.GovernanceRuleEntity;
import io.github.stellorbit.infrastructure.persistence.repository.GovernanceRuleRepository;
import io.github.stellorbit.api.dto.RuleAggregateRequest;
import io.github.stellorbit.api.dto.RuleAggregateResponse;
import io.github.stellorbit.api.dto.RuleDetailMutationRequest;
import io.github.stellorbit.api.dto.RuleMutationRequest;
import io.github.stellorbit.api.dto.RuleSummaryResponse;
import io.github.stellorbit.api.error.InvalidRuleRequestException;
import io.github.stellorbit.api.error.ResourceNotFoundException;
import io.github.stellorbit.api.security.ControlPlaneSecurityContextHolder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public abstract class RuleAggregateService<
    E extends Identifiable,
    D extends RuleDetailMutationRequest,
    R extends RuleAggregateRequest<D>,
    V> {

  private final GovernanceRuleRepository governanceRuleRepository;
  private final JpaRepository<E, UUID> detailRuleRepository;
  private final String ruleType;
  private final String resourceName;

  protected RuleAggregateService(
      GovernanceRuleRepository governanceRuleRepository,
      JpaRepository<E, UUID> detailRuleRepository,
      String ruleType,
      String resourceName) {
    this.governanceRuleRepository = governanceRuleRepository;
    this.detailRuleRepository = detailRuleRepository;
    this.ruleType = ruleType;
    this.resourceName = resourceName;
  }

  /** 查询当前类型的全部聚合规则。 */
  @Transactional(readOnly = true)
  public List<RuleAggregateResponse<V>> findAll() {
    UUID instanceSpaceId = ControlPlaneSecurityContextHolder.requiredInstanceSpaceId();
    return governanceRuleRepository.findByRuleType(ruleType).stream()
        .filter(rule -> instanceSpaceId.equals(rule.getInstanceSpaceId()))
        .map(rule -> toResponse(rule, findDetailRule(rule.getId())))
        .toList();
  }

  /** 按ID查询聚合规则。 */
  @Transactional(readOnly = true)
  public RuleAggregateResponse<V> findById(UUID id) {
    GovernanceRuleEntity governanceRule = findGovernanceRule(id);
    ControlPlaneSecurityContextHolder.requireInstanceSpace(governanceRule.getInstanceSpaceId());
    return toResponse(governanceRule, findDetailRule(id));
  }

  /** 创建聚合规则。 */
  @Transactional
  public RuleAggregateResponse<V> create(R request) {
    validateCreateIds(request);
    ControlPlaneSecurityContextHolder.requireInstanceSpace(request.rule().instanceSpaceId());
    GovernanceRuleEntity governanceRule = toGovernanceRuleEntity(request.rule());
    governanceRule.setId(null);
    governanceRule.setRuleType(ruleType);
    GovernanceRuleEntity savedGovernanceRule = governanceRuleRepository.save(governanceRule);

    E detailRule = toDetailEntity(savedGovernanceRule.getId(), request.detail());
    E savedDetailRule = detailRuleRepository.save(detailRule);
    return toResponse(savedGovernanceRule, savedDetailRule);
  }

  /** 更新聚合规则。 */
  @Transactional
  public RuleAggregateResponse<V> update(UUID id, R request) {
    validateUpdateIds(id, request);
    GovernanceRuleEntity existingRule = findGovernanceRule(id);
    ControlPlaneSecurityContextHolder.requireInstanceSpace(existingRule.getInstanceSpaceId());
    ControlPlaneSecurityContextHolder.requireInstanceSpace(request.rule().instanceSpaceId());
    findDetailRule(id);
    GovernanceRuleEntity governanceRule = toGovernanceRuleEntity(request.rule());
    governanceRule.setId(id);
    governanceRule.setRuleType(ruleType);
    GovernanceRuleEntity savedGovernanceRule = governanceRuleRepository.save(governanceRule);

    E detailRule = toDetailEntity(id, request.detail());
    E savedDetailRule = detailRuleRepository.save(detailRule);
    return toResponse(savedGovernanceRule, savedDetailRule);
  }

  /** 删除聚合规则。 */
  @Transactional
  public void delete(UUID id) {
    GovernanceRuleEntity governanceRule = findGovernanceRule(id);
    ControlPlaneSecurityContextHolder.requireInstanceSpace(governanceRule.getInstanceSpaceId());
    governanceRuleRepository.deleteById(id);
  }

  private GovernanceRuleEntity findGovernanceRule(UUID id) {
    GovernanceRuleEntity governanceRule =
        governanceRuleRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(resourceName, id));
    if (!ruleType.equals(governanceRule.getRuleType())) {
      throw new ResourceNotFoundException(resourceName, id);
    }
    return governanceRule;
  }

  private E findDetailRule(UUID id) {
    return detailRuleRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException(resourceName, id));
  }

  protected abstract E toDetailEntity(UUID id, D detail);

  protected abstract V toDetailResponse(E entity);

  private void validateCreateIds(R request) {
    if (request.rule().id() != null || request.detail().id() != null) {
      throw new InvalidRuleRequestException("创建规则时不允许指定公共规则ID或专属规则ID");
    }
  }

  private void validateUpdateIds(UUID id, R request) {
    validateRequestId("公共规则ID", request.rule().id(), id);
    validateRequestId("专属规则ID", request.detail().id(), id);
  }

  private void validateRequestId(String fieldName, UUID requestId, UUID expectedId) {
    if (requestId != null && !expectedId.equals(requestId)) {
      throw new InvalidRuleRequestException(fieldName + "必须和路径ID一致");
    }
  }

  private RuleAggregateResponse<V> toResponse(GovernanceRuleEntity rule, E detail) {
    return new RuleAggregateResponse<>(toSummaryResponse(rule), toDetailResponse(detail));
  }

  private GovernanceRuleEntity toGovernanceRuleEntity(RuleMutationRequest request) {
    GovernanceRuleEntity entity = new GovernanceRuleEntity();
    entity.setInstanceSpaceId(request.instanceSpaceId());
    entity.setApplicationId(request.applicationId());
    entity.setRuleCode(request.ruleCode());
    entity.setRuleName(request.ruleName());
    entity.setSourceFormat(defaultString(request.sourceFormat(), "CUE"));
    entity.setRuntimeFormat(defaultString(request.runtimeFormat(), "JSON"));
    entity.setCueSource(request.cueSource());
    entity.setRuntimeSnapshotJson(request.runtimeSnapshotJson());
    entity.setChecksum(request.checksum());
    entity.setPriority(defaultInteger(request.priority(), 1000));
    entity.setEnabled(defaultBoolean(request.enabled(), true));
    entity.setStatus(defaultString(request.status(), "DRAFT"));
    entity.setDraftVersion(defaultLong(request.draftVersion(), 1L));
    entity.setDescription(request.description());
    entity.setTags(defaultList(request.tags()));
    entity.setCreatedBy(
        ControlPlaneSecurityContextHolder.current()
            .map(context -> context.operator())
            .orElse(request.createdBy()));
    entity.setUpdatedBy(
        ControlPlaneSecurityContextHolder.current()
            .map(context -> context.operator())
            .orElse(request.updatedBy()));
    return entity;
  }

  private RuleSummaryResponse toSummaryResponse(GovernanceRuleEntity entity) {
    return new RuleSummaryResponse(
        entity.getId(),
        entity.getInstanceSpaceId(),
        entity.getApplicationId(),
        entity.getRuleCode(),
        entity.getRuleName(),
        entity.getRuleType(),
        entity.getSourceFormat(),
        entity.getRuntimeFormat(),
        entity.getChecksum(),
        entity.getPriority(),
        entity.getEnabled(),
        entity.getStatus(),
        entity.getDraftVersion(),
        entity.getLatestReleaseId(),
        entity.getDescription(),
        defaultList(entity.getTags()),
        entity.getCreatedBy(),
        entity.getUpdatedBy(),
        entity.getCreatedAt(),
        entity.getUpdatedAt(),
        entity.getPublishedAt());
  }

  protected Map<String, Object> defaultMap(Map<String, Object> value) {
    return value == null ? new LinkedHashMap<>() : value;
  }

  protected List<Object> defaultList(List<Object> value) {
    return value == null ? new ArrayList<>() : value;
  }

  protected String defaultString(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  protected Integer defaultInteger(Integer value, Integer fallback) {
    return value == null ? fallback : value;
  }

  protected Long defaultLong(Long value, Long fallback) {
    return value == null ? fallback : value;
  }

  protected Boolean defaultBoolean(Boolean value, Boolean fallback) {
    return value == null ? fallback : value;
  }
}
