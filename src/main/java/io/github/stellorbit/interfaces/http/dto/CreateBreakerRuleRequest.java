package io.github.stellorbit.interfaces.http.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record CreateBreakerRuleRequest(
    @Valid @NotNull(message = "公共规则不能为空") RuleMutationRequest rule,
    @Valid @NotNull(message = "熔断规则不能为空") BreakerRuleDetailRequest detail)
    implements RuleAggregateRequest<BreakerRuleDetailRequest> {}
