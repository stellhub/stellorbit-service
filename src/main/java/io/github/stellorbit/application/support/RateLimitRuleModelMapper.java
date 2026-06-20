package io.github.stellorbit.application.support;

import io.github.stellorbit.domain.RateLimitRuleModelSupport;
import io.github.stellorbit.infrastructure.persistence.entity.RateLimitRuleEntity;

public final class RateLimitRuleModelMapper {

  private RateLimitRuleModelMapper() {}

  /** 将限流规则实体转换为企业级限流规则校验模型。 */
  public static RateLimitRuleModelSupport.RateLimitRuleModel fromEntity(
      RateLimitRuleEntity entity) {
    return fromEntity(entity, entity.getCoordinationMode());
  }

  /** 将限流规则实体转换为企业级限流规则校验模型，并使用已归一化的协调模式。 */
  public static RateLimitRuleModelSupport.RateLimitRuleModel fromEntity(
      RateLimitRuleEntity entity, String coordinationMode) {
    return new RateLimitRuleModelSupport.RateLimitRuleModel(
        entity.getLimitMode(),
        entity.getLimitType(),
        entity.getLimitAlgorithm(),
        entity.getTrafficProtocol(),
        coordinationMode,
        entity.getTargetSelector(),
        entity.getRequestMatcher(),
        entity.getKeyExtractor(),
        entity.getDimensions(),
        entity.getQuotaConfig(),
        entity.getWindowConfig(),
        entity.getBurstConfig(),
        entity.getConcurrencyConfig(),
        entity.getHotspotConfig(),
        entity.getCustomPolicy(),
        entity.getModelLimitConfig(),
        entity.getFallbackPolicy(),
        entity.getResponsePolicy(),
        entity.getObservabilityConfig(),
        entity.getShadowConfig());
  }
}
