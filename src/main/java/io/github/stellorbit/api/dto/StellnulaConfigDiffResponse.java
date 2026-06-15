package io.github.stellorbit.api.dto;

import java.util.Map;

public record StellnulaConfigDiffResponse(
    String dataId,
    String publishKind,
    String changeType,
    String baseChecksum,
    String targetChecksum,
    Map<String, Object> baseMetadata,
    Map<String, Object> targetMetadata) {}
