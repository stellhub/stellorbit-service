package io.github.stellorbit.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RateLimitRuleDetailRequest(
    UUID id,
    @NotBlank(message = "限流类型不能为空") String limitType,
    @NotBlank(message = "限流算法不能为空") String limitAlgorithm,
    String enforcementMode,
    Map<String, Object> targetSelector,
    List<Object> dimensions,
    Map<String, Object> quotaConfig,
    Map<String, Object> windowConfig,
    Map<String, Object> burstConfig,
    Map<String, Object> modelLimitConfig,
    Map<String, Object> fallbackPolicy,
    Map<String, Object> responsePolicy)
    implements RuleDetailMutationRequest {}
