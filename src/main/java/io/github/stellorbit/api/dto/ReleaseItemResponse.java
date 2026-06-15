package io.github.stellorbit.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReleaseItemResponse(
    UUID id,
    UUID releaseId,
    UUID ruleId,
    String ruleType,
    String ruleCode,
    String ruleName,
    Long draftVersion,
    Integer priority,
    String checksum,
    OffsetDateTime createdAt) {}
