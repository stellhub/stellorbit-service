package io.github.stellorbit.application.service;

import io.github.stellorbit.infrastructure.persistence.entity.AuthRuleCertificateEntity;
import io.github.stellorbit.infrastructure.persistence.entity.GovernanceRuleEntity;
import io.github.stellorbit.infrastructure.persistence.entity.MtlsCertificateEntity;
import io.github.stellorbit.infrastructure.persistence.repository.AuthRuleCertificateRepository;
import io.github.stellorbit.infrastructure.persistence.repository.GovernanceRuleRepository;
import io.github.stellorbit.infrastructure.persistence.repository.MtlsCertificateRepository;
import io.github.stellorbit.api.error.InvalidRuleRequestException;
import io.github.stellorbit.api.error.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthRuleCertificateService extends CrudService<AuthRuleCertificateEntity> {

  private final AuthRuleCertificateRepository repository;
  private final GovernanceRuleRepository governanceRuleRepository;
  private final MtlsCertificateRepository mtlsCertificateRepository;

  public AuthRuleCertificateService(
      AuthRuleCertificateRepository repository,
      GovernanceRuleRepository governanceRuleRepository,
      MtlsCertificateRepository mtlsCertificateRepository) {
    super(repository, "AuthRuleCertificate");
    this.repository = repository;
    this.governanceRuleRepository = governanceRuleRepository;
    this.mtlsCertificateRepository = mtlsCertificateRepository;
  }

  /** 创建鉴权规则证书绑定。 */
  @Override
  @Transactional
  public AuthRuleCertificateEntity create(AuthRuleCertificateEntity entity) {
    validateBinding(entity);
    entity.setId(null);
    return repository.save(entity);
  }

  /** 更新鉴权规则证书绑定。 */
  @Override
  @Transactional
  public AuthRuleCertificateEntity update(UUID id, AuthRuleCertificateEntity entity) {
    if (!repository.existsById(id)) {
      throw new ResourceNotFoundException("AuthRuleCertificate", id);
    }
    validateBinding(entity);
    entity.setId(id);
    return repository.save(entity);
  }

  private void validateBinding(AuthRuleCertificateEntity entity) {
    GovernanceRuleEntity authRule =
        governanceRuleRepository
            .findById(entity.getAuthRuleId())
            .orElseThrow(
                () -> new ResourceNotFoundException("AuthPolicyRule", entity.getAuthRuleId()));
    if (!"AUTH".equals(authRule.getRuleType())) {
      throw new InvalidRuleRequestException("证书只能绑定到AUTH类型的鉴权规则");
    }

    MtlsCertificateEntity certificate =
        mtlsCertificateRepository
            .findById(entity.getCertificateId())
            .orElseThrow(
                () -> new ResourceNotFoundException("MtlsCertificate", entity.getCertificateId()));
    if (!"ACTIVE".equals(certificate.getStatus())) {
      throw new InvalidRuleRequestException("只能绑定ACTIVE状态的证书");
    }
  }
}
