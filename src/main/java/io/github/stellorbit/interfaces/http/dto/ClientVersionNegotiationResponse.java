package io.github.stellorbit.interfaces.http.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record ClientVersionNegotiationResponse(
    String protocolVersion,
    String snapshotSchemaVersion,
    String runtimeFormat,
    String compatibilityMode,
    String snapshotEndpoint,
    String watchEndpoint,
    String ackEndpoint,
    String statusReportEndpoint,
    String heartbeatEndpoint,
    Map<String, Object> protobufCompatibility,
    RuntimeNodeDirectoryResponse rateLimitNodeDirectory,
    OffsetDateTime negotiatedAt) {}
