package io.github.stellorbit.interfaces.http.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ClientHeartbeatResponse(
    UUID sessionId,
    OffsetDateTime serverTime,
    Long heartbeatTtlMillis,
    Long latestReleaseVersion,
    Boolean needSnapshotRefresh,
    RuntimeNodeDirectoryResponse rateLimitNodeDirectory) {}
