package io.github.stellorbit.api.dto;

public interface RuleAggregateRequest<T extends RuleDetailMutationRequest> {

  RuleMutationRequest rule();

  T detail();
}
