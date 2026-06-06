package io.github.stellorbit.interfaces.http.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record RuleReleaseSummaryResponse(
    UUID id,
    UUID instanceSpaceId,
    UUID applicationId,
    Long releaseVersion,
    String releaseName,
    String releaseStatus,
    String idempotencyKey,
    String checksum,
    UUID rollbackFromReleaseId,
    Integer retryCount,
    Integer maxRetryCount,
    Integer itemCount,
    Integer publishRecordCount,
    Integer failedPublishRecordCount,
    List<Object> failureDetails,
    String createdBy,
    String publishedBy,
    OffsetDateTime createdAt,
    OffsetDateTime publishedAt,
    OffsetDateTime updatedAt) {}
