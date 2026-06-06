package io.github.stellorbit.infrastructure.persistence.entity;

import io.github.stellorbit.domain.Identifiable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "mtls_certificates", schema = "stellorbit")
public class MtlsCertificateEntity implements Identifiable {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @NotNull(message = "实例空间ID不能为空") @Column(name = "instance_space_id", nullable = false)
  private UUID instanceSpaceId;

  @Column(name = "application_id")
  private UUID applicationId;

  @NotBlank(message = "证书编码不能为空") @Column(name = "certificate_code", nullable = false, length = 160)
  private String certificateCode;

  @NotBlank(message = "证书名称不能为空") @Column(name = "certificate_name", nullable = false, length = 160)
  private String certificateName;

  @NotBlank(message = "证书类型不能为空") @Column(name = "certificate_type", nullable = false, length = 48)
  private String certificateType;

  @NotBlank(message = "证书用途不能为空") @Column(name = "usage_type", nullable = false, length = 48)
  private String usageType;

  @Column(name = "trust_domain", length = 160)
  private String trustDomain;

  @Column(name = "subject_dn", columnDefinition = "text")
  private String subjectDn;

  @Column(name = "issuer_dn", columnDefinition = "text")
  private String issuerDn;

  @Column(name = "serial_number", length = 160)
  private String serialNumber;

  @NotBlank(message = "证书指纹不能为空") @Column(name = "fingerprint_sha256", nullable = false, length = 128)
  private String fingerprintSha256;

  @NotBlank(message = "证书链不能为空") @Column(name = "certificate_chain_pem", nullable = false, columnDefinition = "text")
  private String certificateChainPem;

  @NotBlank(message = "公钥证书不能为空") @Column(name = "public_certificate_pem", nullable = false, columnDefinition = "text")
  private String publicCertificatePem;

  @Column(name = "encrypted_private_key")
  private byte[] encryptedPrivateKey;

  @Column(name = "private_key_algorithm", length = 80)
  private String privateKeyAlgorithm;

  @Column(name = "encryption_key_id", length = 160)
  private String encryptionKeyId;

  @NotNull(message = "证书生效时间不能为空") @Column(name = "not_before", nullable = false)
  private OffsetDateTime notBefore;

  @NotNull(message = "证书过期时间不能为空") @Column(name = "not_after", nullable = false)
  private OffsetDateTime notAfter;

  @Column(nullable = false, length = 32)
  private String status = "ACTIVE";

  @NotBlank(message = "创建人不能为空") @Column(name = "created_by", nullable = false, length = 120)
  private String createdBy;

  @NotBlank(message = "更新人不能为空") @Column(name = "updated_by", nullable = false, length = 120)
  private String updatedBy;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;
}
