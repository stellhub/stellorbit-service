package io.github.stellorbit.interfaces.http.dto;

public record RetryRuleReleaseRequest(String operator, String reason, Integer maxRetryCount) {}
