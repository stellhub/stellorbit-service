package io.github.stellorbit.interfaces.http.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RuntimeRuleSnapshotResponse(
    UUID releaseId,
    Long releaseVersion,
    String releaseStatus,
    String protocolVersion,
    String snapshotSchemaVersion,
    String runtimeFormat,
    Boolean changed,
    Boolean grayMatched,
    String compatibilityMode,
    String checksum,
    OffsetDateTime publishedAt,
    OffsetDateTime generatedAt,
    Map<String, Object> snapshotJson,
    String snapshotBytesBase64,
    List<RuntimeSnapshotRuleResponse> rules,
    RuntimeNodeDirectoryResponse rateLimitNodeDirectory) {}
