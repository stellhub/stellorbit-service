package io.github.stellorbit.infrastructure.persistence.entity;

import io.github.stellorbit.domain.Identifiable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "auth_policy_rules", schema = "stellorbit")
public class AuthPolicyRuleEntity implements Identifiable {

  @Id
  @Column(name = "governance_rule_id", nullable = false)
  private UUID id;

  @NotBlank(message = "鉴权策略类型不能为空") @Column(name = "auth_policy_type", nullable = false, length = 48)
  private String authPolicyType;

  @Column(name = "auth_action", length = 32)
  private String authAction;

  @Column(name = "mtls_mode", length = 32)
  private String mtlsMode;

  @Column(name = "trust_domain", length = 160)
  private String trustDomain;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "workload_selector", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> workloadSelector = new LinkedHashMap<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "peer_sources", nullable = false, columnDefinition = "jsonb")
  private List<Object> peerSources = new ArrayList<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "request_authentications", nullable = false, columnDefinition = "jsonb")
  private List<Object> requestAuthentications = new ArrayList<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "authorization_from", nullable = false, columnDefinition = "jsonb")
  private List<Object> authorizationFrom = new ArrayList<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "authorization_to", nullable = false, columnDefinition = "jsonb")
  private List<Object> authorizationTo = new ArrayList<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "authorization_when", nullable = false, columnDefinition = "jsonb")
  private List<Object> authorizationWhen = new ArrayList<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "jwt_rules", nullable = false, columnDefinition = "jsonb")
  private List<Object> jwtRules = new ArrayList<>();

  @Column(name = "ext_authz_provider", length = 160)
  private String extAuthzProvider;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "audit_policy", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> auditPolicy = new LinkedHashMap<>();

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;
}
