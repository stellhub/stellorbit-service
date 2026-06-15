package io.github.stellorbit.infrastructure.persistence.entity;

import io.github.stellorbit.domain.Identifiable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
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
@Table(name = "runtime_table_retention_policies", schema = "stellorbit")
public class RuntimeTableRetentionPolicyEntity implements Identifiable {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @NotBlank(message = "表名不能为空") @Column(name = "table_name", nullable = false, length = 120)
  private String tableName;

  @NotBlank(message = "分区键不能为空") @Column(name = "partition_key", nullable = false, length = 120)
  private String partitionKey;

  @Column(name = "retention_days", nullable = false)
  private Integer retentionDays;

  @Column(name = "cleanup_enabled", nullable = false)
  private Boolean cleanupEnabled = true;

  @Column(name = "archive_enabled", nullable = false)
  private Boolean archiveEnabled = false;

  @Column(name = "policy_status", nullable = false, length = 32)
  private String policyStatus = "ACTIVE";

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;
}
