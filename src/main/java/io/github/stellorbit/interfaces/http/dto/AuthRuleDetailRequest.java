package io.github.stellorbit.interfaces.http.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AuthRuleDetailRequest(
    UUID id,
    @NotBlank(message = "鉴权策略类型不能为空") String authPolicyType,
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
    Map<String, Object> auditPolicy)
    implements RuleDetailMutationRequest {}
