package io.github.stellorbit.api.dto;

import java.util.Map;
import java.util.UUID;

public record DistributedRateLimitRuleConfigResponse(
    UUID instanceSpaceId,
    UUID applicationId,
    String applicationCode,
    String configId,
    String ruleName,
    String ruleType,
    String stellnulaRuleType,
    String publishKind,
    String content,
    String checksum,
    String aggregateChecksum,
    Map<String, Object> contentModel,
    Integer ruleCount) {}
