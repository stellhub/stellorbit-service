package io.github.stellorbit.interfaces.http.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ClientVersionNegotiationRequest(
    @NotNull(message = "实例空间ID不能为空") UUID instanceSpaceId,
    @NotNull(message = "应用ID不能为空") UUID applicationId,
    @NotBlank(message = "客户端ID不能为空") String clientId,
    @NotBlank(message = "客户端版本不能为空") String clientVersion,
    List<String> supportedProtocolVersions,
    List<String> acceptedRuntimeFormats,
    Map<String, Object> labels,
    Map<String, Object> metadata) {}
