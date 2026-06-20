package io.github.stellorbit.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RateLimitRuleDetailRequest(
    UUID id,
    String limitMode,
    @NotBlank(message = "限流类型不能为空") String limitType,
    @NotBlank(message = "限流算法不能为空") String limitAlgorithm,
    String trafficProtocol,
    String executionLocation,
    String coordinationMode,
    String enforcementMode,
    Map<String, Object> targetSelector,
    Map<String, Object> requestMatcher,
    Map<String, Object> keyExtractor,
    List<Object> dimensions,
    Map<String, Object> quotaConfig,
    Map<String, Object> windowConfig,
    Map<String, Object> burstConfig,
    Map<String, Object> concurrencyConfig,
    Map<String, Object> hotspotConfig,
    Map<String, Object> customPolicy,
    Map<String, Object> modelLimitConfig,
    Map<String, Object> fallbackPolicy,
    Map<String, Object> responsePolicy,
    Map<String, Object> observabilityConfig,
    Map<String, Object> shadowConfig)
    implements RuleDetailMutationRequest {}
