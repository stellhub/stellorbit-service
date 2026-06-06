package io.github.stellorbit.infrastructure.persistence.entity;

import io.github.stellorbit.domain.Identifiable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "audit_events", schema = "stellorbit")
public class AuditEventEntity implements Identifiable {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @NotBlank(message = "审计事件类型不能为空") @Column(name = "event_type", nullable = false, length = 80)
  private String eventType;

  @NotBlank(message = "审计资源类型不能为空") @Column(name = "resource_type", nullable = false, length = 80)
  private String resourceType;

  @Column(name = "resource_id")
  private UUID resourceId;

  @Column(name = "tenant_id", nullable = false, length = 120)
  private String tenantId;

  @Column(name = "instance_space_id")
  private UUID instanceSpaceId;

  @Column(name = "application_id")
  private UUID applicationId;

  @NotBlank(message = "操作人不能为空") @Column(nullable = false, length = 120)
  private String operator;

  @Column(name = "operator_ip", columnDefinition = "inet")
  private InetAddress operatorIp;

  @Column(name = "user_agent", columnDefinition = "text")
  private String userAgent;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "event_detail", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> eventDetail = new LinkedHashMap<>();

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;
}
