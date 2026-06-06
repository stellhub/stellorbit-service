package io.github.stellorbit.interfaces.http.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RateLimitQuotaLeaseResponse(
    UUID assignmentId,
    UUID rateLimitRuleId,
    UUID releaseId,
    String clientId,
    String limitKeyHash,
    Long assignedQuota,
    Long remainingQuota,
    Long leaseVersion,
    String leaseStatus,
    OffsetDateTime assignedAt,
    OffsetDateTime expiresAt) {}
