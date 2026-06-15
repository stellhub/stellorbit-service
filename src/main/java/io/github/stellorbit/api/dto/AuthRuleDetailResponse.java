package io.github.stellorbit.api.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AuthRuleDetailResponse(
    UUID id,
    String authPolicyType,
    String authAction,
    String mtlsMode,
    String trustDomain,
    Map<String, Object> workloadSelector,
    List<Object> peerSources,
    List<Object> requestAuthentications,
    List<Object> authorizationFrom,
    List<Object> authorizationTo,
    List<Object> authorizationWhen,
    List<Object> jwtRules,
    String extAuthzProvider,
    Map<String, Object> auditPolicy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
