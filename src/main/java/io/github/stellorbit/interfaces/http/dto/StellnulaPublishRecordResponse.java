package io.github.stellorbit.interfaces.http.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record StellnulaPublishRecordResponse(
    UUID id,
    UUID releaseId,
    String publishKind,
    String namespaceCode,
    String configGroup,
    String configKey,
    String dataId,
    String contentType,
    String runtimeFormat,
    Map<String, Object> payloadMetadata,
    String checksum,
    String targetVersion,
    String publishStatus,
    String idempotencyKey,
    Integer retryCount,
    Integer maxRetryCount,
    OffsetDateTime nextRetryAt,
    OffsetDateTime lastAttemptAt,
    List<Object> failureDetails,
    String errorMessage,
    String recoveredBy,
    OffsetDateTime recoveredAt,
    String recoveryNote,
    OffsetDateTime publishedAt,
    OffsetDateTime createdAt) {}
