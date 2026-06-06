package io.github.stellorbit.interfaces.http.dto;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record ApprovalResponse(
    UUID auditEventId,
    UUID releaseId,
    String approvalStatus,
    String operator,
    String reason,
    Map<String, Object> detail,
    OffsetDateTime createdAt) {}
