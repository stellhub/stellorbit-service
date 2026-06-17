package io.github.stellorbit.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ClientHeartbeatResponse(
    UUID sessionId,
    OffsetDateTime serverTime,
    Long heartbeatTtlMillis,
    Long latestReleaseVersion,
    Boolean needSnapshotRefresh) {}
