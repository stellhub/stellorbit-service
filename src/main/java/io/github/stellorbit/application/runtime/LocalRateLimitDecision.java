package io.github.stellorbit.application.runtime;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LocalRateLimitDecision(
    UUID decisionId,
    UUID bucketId,
    String limitKeyHash,
    String originalLimitKeyHash,
    Integer hotspotShard,
    String windowStrategy,
    OffsetDateTime windowStartAt,
    OffsetDateTime windowEndAt,
    long quota,
    long requestedPermits,
    long usedPermits,
    long remainingPermits,
    boolean allowed,
    Long retryAfterMillis,
    String decisionReason,
    boolean fallbackUsed,
    OffsetDateTime resetAt) {}
