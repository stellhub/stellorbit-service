package io.github.stellorbit.application.service;

import io.github.stellorbit.api.dto.CreateRateLimitRuleRequest;
import io.github.stellorbit.api.dto.RateLimitRuleDetailRequest;
import io.github.stellorbit.api.dto.RateLimitRuleDetailResponse;
import io.github.stellorbit.api.dto.RuleAggregateResponse;
import io.github.stellorbit.application.event.RateLimitRuleChangedEvent;
import io.github.stellorbit.domain.RateLimitRuleModeSupport;
import io.github.stellorbit.infrastructure.persistence.entity.RateLimitRuleEntity;
import io.github.stellorbit.infrastructure.persistence.repository.GovernanceRuleRepository;
import io.github.stellorbit.infrastructure.persistence.repository.RateLimitRuleRepository;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RateLimitRuleService
    extends RuleAggregateService<
        RateLimitRuleEntity,
        RateLimitRuleDetailRequest,
        CreateRateLimitRuleRequest,
        RateLimitRuleDetailResponse> {

  private final ApplicationEventPublisher eventPublisher;

  public RateLimitRuleService(
      GovernanceRuleRepository governanceRuleRepository,
      RateLimitRuleRepository rateLimitRuleRepository,
      ApplicationEventPublisher eventPublisher) {
    super(governanceRuleRepository, rateLimitRuleRepository, "RATE_LIMIT", "RateLimitRule");
    this.eventPublisher = eventPublisher;
  }

  /** 创建限流规则，并在事务提交后刷新分布式限流规则内存快照。 */
  @Override
  @Transactional
  public RuleAggregateResponse<RateLimitRuleDetailResponse> create(
      CreateRateLimitRuleRequest request) {
    RuleAggregateResponse<RateLimitRuleDetailResponse> response = super.create(request);
    publishRateLimitRuleChanged(response.rule().id(), "CREATE");
    return response;
  }

  /** 更新限流规则，并在事务提交后刷新分布式限流规则内存快照。 */
  @Override
  @Transactional
  public RuleAggregateResponse<RateLimitRuleDetailResponse> update(
      UUID id, CreateRateLimitRuleRequest request) {
    RuleAggregateResponse<RateLimitRuleDetailResponse> response = super.update(id, request);
    publishRateLimitRuleChanged(id, "UPDATE");
    return response;
  }

  /** 删除限流规则，并在事务提交后刷新分布式限流规则内存快照。 */
  @Override
  @Transactional
  public void delete(UUID id) {
    super.delete(id);
    publishRateLimitRuleChanged(id, "DELETE");
  }

  @Override
  protected RateLimitRuleEntity toDetailEntity(UUID id, RateLimitRuleDetailRequest detail) {
    RateLimitRuleEntity entity = new RateLimitRuleEntity();
    entity.setId(id);
    entity.setLimitType(detail.limitType());
    entity.setLimitAlgorithm(detail.limitAlgorithm());
    String executionLocation =
        RateLimitRuleModeSupport.normalizeExecutionLocation(
            detail.executionLocation(), detail.enforcementMode());
    String coordinationMode =
        RateLimitRuleModeSupport.normalizeCoordinationMode(
            detail.coordinationMode(), detail.enforcementMode());
    entity.setExecutionLocation(executionLocation);
    entity.setCoordinationMode(coordinationMode);
    entity.setEnforcementMode(
        RateLimitRuleModeSupport.toLegacyEnforcementMode(executionLocation, coordinationMode));
    entity.setTargetSelector(defaultMap(detail.targetSelector()));
    entity.setDimensions(defaultList(detail.dimensions()));
    entity.setQuotaConfig(defaultMap(detail.quotaConfig()));
    entity.setWindowConfig(defaultMap(detail.windowConfig()));
    entity.setBurstConfig(defaultMap(detail.burstConfig()));
    entity.setModelLimitConfig(defaultMap(detail.modelLimitConfig()));
    entity.setFallbackPolicy(defaultMap(detail.fallbackPolicy()));
    entity.setResponsePolicy(defaultMap(detail.responsePolicy()));
    return entity;
  }

  @Override
  protected RateLimitRuleDetailResponse toDetailResponse(RateLimitRuleEntity entity) {
    String executionLocation =
        RateLimitRuleModeSupport.normalizeExecutionLocation(
            entity.getExecutionLocation(), entity.getEnforcementMode());
    String coordinationMode =
        RateLimitRuleModeSupport.normalizeCoordinationMode(
            entity.getCoordinationMode(), entity.getEnforcementMode());
    return new RateLimitRuleDetailResponse(
        entity.getId(),
        entity.getLimitType(),
        entity.getLimitAlgorithm(),
        executionLocation,
        coordinationMode,
        RateLimitRuleModeSupport.toLegacyEnforcementMode(executionLocation, coordinationMode),
        entity.getTargetSelector(),
        entity.getDimensions(),
        entity.getQuotaConfig(),
        entity.getWindowConfig(),
        entity.getBurstConfig(),
        entity.getModelLimitConfig(),
        entity.getFallbackPolicy(),
        entity.getResponsePolicy(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }

  private void publishRateLimitRuleChanged(UUID ruleId, String action) {
    eventPublisher.publishEvent(new RateLimitRuleChangedEvent(ruleId, action));
  }
}
