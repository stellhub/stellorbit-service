package io.github.stellorbit.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EvaluateRateLimitResponse(
    UUID decisionId,
    UUID bucketId,
    UUID rateLimitRuleId,
    UUID releaseId,
    String limitKeyHash,
    String originalLimitKeyHash,
    Integer hotspotShard,
    String windowStrategy,
    OffsetDateTime windowStartAt,
    OffsetDateTime windowEndAt,
    Long quota,
    Long requestedPermits,
    Long usedPermits,
    Long remainingPermits,
    Boolean allowed,
    Long retryAfterMillis,
    String decisionReason,
    Boolean fallbackUsed,
    OffsetDateTime resetAt) {}
