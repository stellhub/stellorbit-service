package io.github.stellorbit.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record BreakerRuleDetailRequest(
    UUID id,
    @NotBlank(message = "熔断类型不能为空") String breakerType,
    @NotBlank(message = "协议不能为空") String protocol,
    Map<String, Object> targetSelector,
    String windowType,
    Long windowSize,
    Long minimumCalls,
    BigDecimal failureRateThreshold,
    BigDecimal slowCallRateThreshold,
    Long slowCallDurationMillis,
    Long openStateWaitMillis,
    Long permittedHalfOpenCalls,
    Map<String, Object> connectionPoolPolicy,
    Map<String, Object> outlierDetectionPolicy,
    Map<String, Object> retryBudgetPolicy,
    Map<String, Object> exceptionRecordPolicy,
    Map<String, Object> exceptionIgnorePolicy,
    Map<String, Object> fallbackPolicy)
    implements RuleDetailMutationRequest {}
