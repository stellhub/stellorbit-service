package io.github.stellorbit.application.port;

import java.util.Map;

public record StellnulaPublishCommand(
    String ruleId,
    String ruleName,
    String ownerId,
    String ownerType,
    String namespaceCode,
    String configGroup,
    String publishKind,
    String env,
    String region,
    String zone,
    String cluster,
    String scopeMode,
    String contentType,
    boolean sensitive,
    String description,
    String content,
    String reason,
    String operator,
    Map<String, Object> metadata,
    String checksum) {}
