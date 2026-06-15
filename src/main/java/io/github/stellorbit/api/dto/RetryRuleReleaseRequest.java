package io.github.stellorbit.api.dto;

public record RetryRuleReleaseRequest(String operator, String reason, Integer maxRetryCount) {}
