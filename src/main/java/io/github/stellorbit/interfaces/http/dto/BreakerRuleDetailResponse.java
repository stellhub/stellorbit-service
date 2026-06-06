package io.github.stellorbit.interfaces.http.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record BreakerRuleDetailResponse(
    UUID id,
    String breakerType,
    String protocol,
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
    Map<String, Object> fallbackPolicy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
