package io.github.stellorbit.interfaces.http.dto;

import java.time.OffsetDateTime;

public record RuntimeNodeResponse(
    String nodeId,
    String address,
    Integer weight,
    Boolean current,
    String healthStatus,
    OffsetDateTime lastHeartbeatAt) {}
