package io.github.stellorbit.api.dto;

import java.time.OffsetDateTime;

public record RuntimeNodeResponse(
    String nodeId,
    String address,
    Integer weight,
    Boolean current,
    String healthStatus,
    OffsetDateTime lastHeartbeatAt) {}
