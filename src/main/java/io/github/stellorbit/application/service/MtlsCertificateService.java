package io.github.stellorbit.application.service;

import io.github.stellorbit.infrastructure.persistence.entity.MtlsCertificateEntity;
import io.github.stellorbit.infrastructure.persistence.repository.MtlsCertificateRepository;
import io.github.stellorbit.api.error.InvalidRuleRequestException;
import io.github.stellorbit.api.error.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MtlsCertificateService extends CrudService<MtlsCertificateEntity> {

  private final MtlsCertificateRepository repository;

  public MtlsCertificateService(MtlsCertificateRepository repository) {
    super(repository, "MtlsCertificate");
    this.repository = repository;
  }

  /** 创建mTLS证书。 */
  @Override
  @Transactional
  public MtlsCertificateEntity create(MtlsCertificateEntity entity) {
    validateValidity(entity);
    validateUniqueFingerprint(entity.getFingerprintSha256(), null);
    entity.setId(null);
    return repository.save(entity);
  }

  /** 更新mTLS证书。 */
  @Override
  @Transactional
  public MtlsCertificateEntity update(UUID id, MtlsCertificateEntity entity) {
    if (!repository.existsById(id)) {
      throw new ResourceNotFoundException("MtlsCertificate", id);
    }
    validateValidity(entity);
    validateUniqueFingerprint(entity.getFingerprintSha256(), id);
    entity.setId(id);
    return repository.save(entity);
  }

  private void validateValidity(MtlsCertificateEntity entity) {
    if (entity.getNotBefore() == null || entity.getNotAfter() == null) {
      return;
    }
    if (!entity.getNotAfter().isAfter(entity.getNotBefore())) {
      throw new InvalidRuleRequestException("证书过期时间必须晚于证书生效时间");
    }
  }

  private void validateUniqueFingerprint(String fingerprintSha256, UUID currentId) {
    if (fingerprintSha256 == null || fingerprintSha256.isBlank()) {
      return;
    }
    boolean exists =
        currentId == null
            ? repository.existsByFingerprintSha256(fingerprintSha256)
            : repository.existsByFingerprintSha256AndIdNot(fingerprintSha256, currentId);
    if (exists) {
      throw new InvalidRuleRequestException("证书指纹已存在");
    }
  }
}
