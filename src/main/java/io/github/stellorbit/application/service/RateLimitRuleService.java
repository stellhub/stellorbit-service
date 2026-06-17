package io.github.stellorbit.application.service;

import io.github.stellorbit.api.dto.CreateRateLimitRuleRequest;
import io.github.stellorbit.api.dto.RateLimitRuleDetailRequest;
import io.github.stellorbit.api.dto.RateLimitRuleDetailResponse;
import io.github.stellorbit.infrastructure.persistence.entity.RateLimitRuleEntity;
import io.github.stellorbit.infrastructure.persistence.repository.GovernanceRuleRepository;
import io.github.stellorbit.infrastructure.persistence.repository.RateLimitRuleRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RateLimitRuleService
    extends RuleAggregateService<
        RateLimitRuleEntity,
        RateLimitRuleDetailRequest,
        CreateRateLimitRuleRequest,
        RateLimitRuleDetailResponse> {

  public RateLimitRuleService(
      GovernanceRuleRepository governanceRuleRepository,
      RateLimitRuleRepository rateLimitRuleRepository) {
    super(governanceRuleRepository, rateLimitRuleRepository, "RATE_LIMIT", "RateLimitRule");
  }

  @Override
  protected RateLimitRuleEntity toDetailEntity(UUID id, RateLimitRuleDetailRequest detail) {
    RateLimitRuleEntity entity = new RateLimitRuleEntity();
    entity.setId(id);
    entity.setLimitType(detail.limitType());
    entity.setLimitAlgorithm(detail.limitAlgorithm());
    entity.setEnforcementMode(defaultString(detail.enforcementMode(), "LOCAL"));
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
    return new RateLimitRuleDetailResponse(
        entity.getId(),
        entity.getLimitType(),
        entity.getLimitAlgorithm(),
        entity.getEnforcementMode(),
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
}
