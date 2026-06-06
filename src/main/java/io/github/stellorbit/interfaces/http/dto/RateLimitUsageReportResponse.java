package io.github.stellorbit.interfaces.http.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RateLimitUsageReportResponse(
    UUID reportId,
    UUID assignmentId,
    UUID rateLimitRuleId,
    UUID releaseId,
    String clientId,
    String limitKeyHash,
    Long reportedUsed,
    Long reportedAllowed,
    Long reportedRejected,
    OffsetDateTime reportedAt) {}
