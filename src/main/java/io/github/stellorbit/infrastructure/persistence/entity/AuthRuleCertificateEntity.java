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

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "auth_rule_certificates", schema = "stellorbit")
public class AuthRuleCertificateEntity implements Identifiable {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @NotNull(message = "鉴权规则ID不能为空") @Column(name = "auth_rule_id", nullable = false)
  private UUID authRuleId;

  @NotNull(message = "证书ID不能为空") @Column(name = "certificate_id", nullable = false)
  private UUID certificateId;

  @NotBlank(message = "绑定类型不能为空") @Column(name = "binding_type", nullable = false, length = 48)
  private String bindingType;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;
}
