package io.github.stellorbit.api.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record RuleReleaseResponse(
    UUID id,
    UUID instanceSpaceId,
    UUID applicationId,
    Long releaseVersion,
    String releaseName,
    String releaseStatus,
    String idempotencyKey,
    String sourceFormat,
    String runtimeFormat,
    String checksum,
    UUID rollbackFromReleaseId,
    String releaseNote,
    Integer retryCount,
    Integer maxRetryCount,
    List<Object> failureDetails,
    String recoveryStatus,
    String recoveredBy,
    OffsetDateTime recoveredAt,
    String recoveryNote,
    String createdBy,
    String publishedBy,
    OffsetDateTime createdAt,
    OffsetDateTime publishedAt,
    OffsetDateTime updatedAt,
    List<ReleaseItemResponse> items,
    List<StellnulaPublishRecordResponse> publishRecords) {}
