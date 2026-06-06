package io.github.stellorbit.interfaces.http.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RateLimitQuotaLeaseRequest(
    @NotNull(message = "限流规则ID不能为空") UUID rateLimitRuleId,
    @NotBlank(message = "客户端ID不能为空") String clientId,
    @NotBlank(message = "限流Key不能为空") String limitKey) {}
