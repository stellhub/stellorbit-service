package io.github.stellorbit.api.dto;

public record RuleAggregateResponse<T>(RuleSummaryResponse rule, T detail) {}
