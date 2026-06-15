package io.github.stellorbit.api.dto;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record ClientRuntimeInstanceResponse(
    UUID sessionId,
    String clientId,
    String clientVersion,
    String protocolVersion,
    String snapshotSchemaVersion,
    String runtimeFormat,
    UUID currentReleaseId,
    Long currentReleaseVersion,
    String clientAddress,
    String zone,
    Map<String, Object> labels,
    String rateLimitRingVersion,
    String sessionStatus,
    OffsetDateTime firstSeenAt,
    OffsetDateTime lastHeartbeatAt) {}
