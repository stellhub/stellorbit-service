package io.github.stellorbit.interfaces.http.dto;

public record RuleAggregateResponse<T>(RuleSummaryResponse rule, T detail) {}
