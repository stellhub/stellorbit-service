package io.github.stellorbit.interfaces.http.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record CreateAuthPolicyRuleRequest(
    @Valid @NotNull(message = "公共规则不能为空") RuleMutationRequest rule,
    @Valid @NotNull(message = "鉴权规则不能为空") AuthRuleDetailRequest detail)
    implements RuleAggregateRequest<AuthRuleDetailRequest> {}
