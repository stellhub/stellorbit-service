package io.github.stellorbit.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.Map;
import java.util.UUID;

public record EvaluateRateLimitRequest(
    @NotNull(message = "限流规则ID不能为空") UUID rateLimitRuleId,
    UUID releaseId,
    String requestId,
    String clientId,
    @NotBlank(message = "限流Key不能为空") String limitKey,
    @Positive(message = "请求配额必须大于0") Long requestedPermits,
    Map<String, Object> modelRequestUnits) {}
