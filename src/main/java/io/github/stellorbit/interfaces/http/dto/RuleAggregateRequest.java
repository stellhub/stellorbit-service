package io.github.stellorbit.interfaces.http.dto;

public interface RuleAggregateRequest<T extends RuleDetailMutationRequest> {

  RuleMutationRequest rule();

  T detail();
}
