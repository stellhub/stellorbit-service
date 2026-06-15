package io.github.stellorbit.application.service;

import io.github.stellorbit.infrastructure.persistence.entity.AuthPolicyRuleEntity;
import io.github.stellorbit.infrastructure.persistence.repository.AuthPolicyRuleRepository;
import io.github.stellorbit.infrastructure.persistence.repository.GovernanceRuleRepository;
import io.github.stellorbit.api.dto.AuthRuleDetailRequest;
import io.github.stellorbit.api.dto.AuthRuleDetailResponse;
import io.github.stellorbit.api.dto.CreateAuthPolicyRuleRequest;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AuthPolicyRuleService
    extends RuleAggregateService<
        AuthPolicyRuleEntity,
        AuthRuleDetailRequest,
        CreateAuthPolicyRuleRequest,
        AuthRuleDetailResponse> {

  public AuthPolicyRuleService(
      GovernanceRuleRepository governanceRuleRepository,
      AuthPolicyRuleRepository authPolicyRuleRepository) {
    super(governanceRuleRepository, authPolicyRuleRepository, "AUTH", "AuthPolicyRule");
  }

  @Override
  protected AuthPolicyRuleEntity toDetailEntity(UUID id, AuthRuleDetailRequest detail) {
    AuthPolicyRuleEntity entity = new AuthPolicyRuleEntity();
    entity.setId(id);
    entity.setAuthPolicyType(detail.authPolicyType());
    entity.setAuthAction(detail.authAction());
    entity.setMtlsMode(detail.mtlsMode());
    entity.setTrustDomain(detail.trustDomain());
    entity.setWorkloadSelector(defaultMap(detail.workloadSelector()));
    entity.setPeerSources(defaultList(detail.peerSources()));
    entity.setRequestAuthentications(defaultList(detail.requestAuthentications()));
    entity.setAuthorizationFrom(defaultList(detail.authorizationFrom()));
    entity.setAuthorizationTo(defaultList(detail.authorizationTo()));
    entity.setAuthorizationWhen(defaultList(detail.authorizationWhen()));
    entity.setJwtRules(defaultList(detail.jwtRules()));
    entity.setExtAuthzProvider(detail.extAuthzProvider());
    entity.setAuditPolicy(defaultMap(detail.auditPolicy()));
    return entity;
  }

  @Override
  protected AuthRuleDetailResponse toDetailResponse(AuthPolicyRuleEntity entity) {
    return new AuthRuleDetailResponse(
        entity.getId(),
        entity.getAuthPolicyType(),
        entity.getAuthAction(),
        entity.getMtlsMode(),
        entity.getTrustDomain(),
        entity.getWorkloadSelector(),
        entity.getPeerSources(),
        entity.getRequestAuthentications(),
        entity.getAuthorizationFrom(),
        entity.getAuthorizationTo(),
        entity.getAuthorizationWhen(),
        entity.getJwtRules(),
        entity.getExtAuthzProvider(),
        entity.getAuditPolicy(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }
}
