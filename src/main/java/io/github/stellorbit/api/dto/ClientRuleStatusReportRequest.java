package io.github.stellorbit.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ClientRuleStatusReportRequest(
    @NotNull(message = "实例空间ID不能为空") UUID instanceSpaceId,
    @NotNull(message = "应用ID不能为空") UUID applicationId,
    UUID releaseId,
    Long releaseVersion,
    @NotBlank(message = "客户端ID不能为空") String clientId,
    @NotBlank(message = "客户端版本不能为空") String clientVersion,
    @NotBlank(message = "协议版本不能为空") String protocolVersion,
    @NotBlank(message = "快照Schema版本不能为空") String snapshotSchemaVersion,
    @NotBlank(message = "运行时格式不能为空") String runtimeFormat,
    @NotBlank(message = "生效状态不能为空") String effectiveStatus,
    List<Object> ruleStatuses,
    List<Object> errorDetails,
    OffsetDateTime reportedAt) {}
