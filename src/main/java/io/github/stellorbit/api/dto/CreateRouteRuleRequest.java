package io.github.stellorbit.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record CreateRouteRuleRequest(
    @Valid @NotNull(message = "公共规则不能为空") RuleMutationRequest rule,
    @Valid @NotNull(message = "路由规则不能为空") RouteRuleDetailRequest detail)
    implements RuleAggregateRequest<RouteRuleDetailRequest> {}
