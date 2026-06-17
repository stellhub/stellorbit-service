package io.github.stellorbit.application.service;

import io.github.stellorbit.api.dto.BreakerRuleDetailRequest;
import io.github.stellorbit.api.dto.BreakerRuleDetailResponse;
import io.github.stellorbit.api.dto.CreateBreakerRuleRequest;
import io.github.stellorbit.infrastructure.persistence.entity.BreakerRuleEntity;
import io.github.stellorbit.infrastructure.persistence.repository.BreakerRuleRepository;
import io.github.stellorbit.infrastructure.persistence.repository.GovernanceRuleRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class BreakerRuleService
    extends RuleAggregateService<
        BreakerRuleEntity,
        BreakerRuleDetailRequest,
        CreateBreakerRuleRequest,
        BreakerRuleDetailResponse> {

  public BreakerRuleService(
      GovernanceRuleRepository governanceRuleRepository,
      BreakerRuleRepository breakerRuleRepository) {
    super(governanceRuleRepository, breakerRuleRepository, "BREAKER", "BreakerRule");
  }

  @Override
  protected BreakerRuleEntity toDetailEntity(UUID id, BreakerRuleDetailRequest detail) {
    BreakerRuleEntity entity = new BreakerRuleEntity();
    entity.setId(id);
    entity.setBreakerType(detail.breakerType());
    entity.setProtocol(detail.protocol());
    entity.setTargetSelector(defaultMap(detail.targetSelector()));
    entity.setWindowType(detail.windowType());
    entity.setWindowSize(detail.windowSize());
    entity.setMinimumCalls(detail.minimumCalls());
    entity.setFailureRateThreshold(detail.failureRateThreshold());
    entity.setSlowCallRateThreshold(detail.slowCallRateThreshold());
    entity.setSlowCallDurationMillis(detail.slowCallDurationMillis());
    entity.setOpenStateWaitMillis(detail.openStateWaitMillis());
    entity.setPermittedHalfOpenCalls(detail.permittedHalfOpenCalls());
    entity.setConnectionPoolPolicy(defaultMap(detail.connectionPoolPolicy()));
    entity.setOutlierDetectionPolicy(defaultMap(detail.outlierDetectionPolicy()));
    entity.setRetryBudgetPolicy(defaultMap(detail.retryBudgetPolicy()));
    entity.setExceptionRecordPolicy(defaultMap(detail.exceptionRecordPolicy()));
    entity.setExceptionIgnorePolicy(defaultMap(detail.exceptionIgnorePolicy()));
    entity.setFallbackPolicy(defaultMap(detail.fallbackPolicy()));
    return entity;
  }

  @Override
  protected BreakerRuleDetailResponse toDetailResponse(BreakerRuleEntity entity) {
    return new BreakerRuleDetailResponse(
        entity.getId(),
        entity.getBreakerType(),
        entity.getProtocol(),
        entity.getTargetSelector(),
        entity.getWindowType(),
        entity.getWindowSize(),
        entity.getMinimumCalls(),
        entity.getFailureRateThreshold(),
        entity.getSlowCallRateThreshold(),
        entity.getSlowCallDurationMillis(),
        entity.getOpenStateWaitMillis(),
        entity.getPermittedHalfOpenCalls(),
        entity.getConnectionPoolPolicy(),
        entity.getOutlierDetectionPolicy(),
        entity.getRetryBudgetPolicy(),
        entity.getExceptionRecordPolicy(),
        entity.getExceptionIgnorePolicy(),
        entity.getFallbackPolicy(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }
}
