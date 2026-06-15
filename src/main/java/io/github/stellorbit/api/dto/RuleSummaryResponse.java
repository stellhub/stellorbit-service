package io.github.stellorbit.api.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record RuleSummaryResponse(
    UUID id,
    UUID instanceSpaceId,
    UUID applicationId,
    String ruleCode,
    String ruleName,
    String ruleType,
    String sourceFormat,
    String runtimeFormat,
    String checksum,
    Integer priority,
    Boolean enabled,
    String status,
    Long draftVersion,
    UUID latestReleaseId,
    String description,
    List<Object> tags,
    String createdBy,
    String updatedBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    OffsetDateTime publishedAt) {}
