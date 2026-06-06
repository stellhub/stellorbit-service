package io.github.stellorbit.interfaces.http.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RuleReleaseImpactResponse(
    UUID releaseId,
    UUID instanceSpaceId,
    UUID applicationId,
    Integer ruleCount,
    Map<String, Long> ruleTypeCounts,
    Integer configCount,
    Map<String, Long> publishKindCounts,
    List<String> configIds,
    List<UUID> impactedRuleIds,
    Boolean containsAuthPolicy,
    Boolean containsMtlsCertificate,
    Boolean containsJwks,
    Boolean containsSensitiveConfig) {}
