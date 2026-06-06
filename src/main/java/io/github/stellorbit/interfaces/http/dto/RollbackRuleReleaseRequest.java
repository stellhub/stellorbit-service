package io.github.stellorbit.interfaces.http.dto;

import jakarta.validation.constraints.Positive;

public record RollbackRuleReleaseRequest(
    @Positive(message = "发布版本必须大于0") Long releaseVersion,
    String releaseName,
    String idempotencyKey,
    Integer maxRetryCount,
    String reason,
    String configGroup,
    String operator) {}
