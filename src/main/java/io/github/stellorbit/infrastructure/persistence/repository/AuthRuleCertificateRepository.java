package io.github.stellorbit.infrastructure.persistence.repository;

import io.github.stellorbit.infrastructure.persistence.entity.AuthRuleCertificateEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthRuleCertificateRepository
    extends JpaRepository<AuthRuleCertificateEntity, UUID> {

  List<AuthRuleCertificateEntity> findByAuthRuleId(UUID authRuleId);
}
