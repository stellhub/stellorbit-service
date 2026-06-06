package io.github.stellorbit.interfaces.http.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record CreateRateLimitRuleRequest(
    @Valid @NotNull(message = "公共规则不能为空") RuleMutationRequest rule,
    @Valid @NotNull(message = "限流规则不能为空") RateLimitRuleDetailRequest detail)
    implements RuleAggregateRequest<RateLimitRuleDetailRequest> {}
