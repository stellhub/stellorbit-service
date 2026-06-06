package io.github.stellorbit.infrastructure.persistence.repository;

import io.github.stellorbit.infrastructure.persistence.entity.MtlsCertificateEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MtlsCertificateRepository extends JpaRepository<MtlsCertificateEntity, UUID> {

  boolean existsByFingerprintSha256(String fingerprintSha256);

  boolean existsByFingerprintSha256AndIdNot(String fingerprintSha256, UUID id);
}
