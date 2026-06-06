package io.github.stellorbit.interfaces.http.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ClientRuleAckRequest(
    @NotNull(message = "实例空间ID不能为空") UUID instanceSpaceId,
    @NotNull(message = "应用ID不能为空") UUID applicationId,
    @NotNull(message = "发布ID不能为空") UUID releaseId,
    @NotNull(message = "发布版本不能为空") Long releaseVersion,
    @NotBlank(message = "客户端ID不能为空") String clientId,
    @NotBlank(message = "客户端版本不能为空") String clientVersion,
    @NotBlank(message = "协议版本不能为空") String protocolVersion,
    @NotBlank(message = "快照Schema版本不能为空") String snapshotSchemaVersion,
    @NotBlank(message = "运行时格式不能为空") String runtimeFormat,
    @NotBlank(message = "ACK状态不能为空") String ackStatus,
    @NotBlank(message = "校验和不能为空") String checksum,
    String message,
    OffsetDateTime appliedAt) {}
