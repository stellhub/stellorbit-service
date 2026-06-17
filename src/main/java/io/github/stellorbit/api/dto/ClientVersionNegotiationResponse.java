package io.github.stellorbit.api.dto;

import java.time.OffsetDateTime;

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
    OffsetDateTime negotiatedAt) {}
