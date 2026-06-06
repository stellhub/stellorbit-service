package io.github.stellorbit.interfaces.http.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;

public record ClientHeartbeatRequest(
    @NotNull(message = "实例空间ID不能为空") UUID instanceSpaceId,
    @NotNull(message = "应用ID不能为空") UUID applicationId,
    @NotBlank(message = "客户端ID不能为空") String clientId,
    @NotBlank(message = "客户端版本不能为空") String clientVersion,
    @NotBlank(message = "协议版本不能为空") String protocolVersion,
    @NotBlank(message = "快照Schema版本不能为空") String snapshotSchemaVersion,
    @NotBlank(message = "运行时格式不能为空") String runtimeFormat,
    UUID currentReleaseId,
    Long currentReleaseVersion,
    String clientAddress,
    String zone,
    Map<String, Object> labels,
    Map<String, Object> metadata) {}
