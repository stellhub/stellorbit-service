package io.github.stellorbit.api.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RuleValidationResponse(
    UUID id,
    UUID ruleId,
    Long draftVersion,
    String sourceFormat,
    String validationStatus,
    Map<String, Object> normalizedSnapshotJson,
    List<String> errorMessages,
    List<String> warningMessages,
    String validatedBy,
    OffsetDateTime validatedAt) {}
