package io.github.stellorbit.api.dto;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record ApprovalResponse(
    UUID approvalId,
    UUID releaseId,
    UUID taskId,
    String approvalStatus,
    String operator,
    String reason,
    Map<String, Object> detail,
    OffsetDateTime createdAt) {}
