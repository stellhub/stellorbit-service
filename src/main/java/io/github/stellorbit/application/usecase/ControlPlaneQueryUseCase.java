package io.github.stellorbit.application.usecase;

import io.github.stellorbit.infrastructure.persistence.entity.AuditEventEntity;
import io.github.stellorbit.infrastructure.persistence.entity.GovernanceRuleEntity;
import io.github.stellorbit.infrastructure.persistence.entity.ReleaseItemEntity;
import io.github.stellorbit.infrastructure.persistence.entity.RuleReleaseEntity;
import io.github.stellorbit.infrastructure.persistence.entity.RuleValidationEntity;
import io.github.stellorbit.infrastructure.persistence.entity.StellnulaPublishRecordEntity;
import io.github.stellorbit.infrastructure.persistence.repository.AuditEventRepository;
import io.github.stellorbit.infrastructure.persistence.repository.GovernanceRuleRepository;
import io.github.stellorbit.infrastructure.persistence.repository.ReleaseItemRepository;
import io.github.stellorbit.infrastructure.persistence.repository.RuleReleaseRepository;
import io.github.stellorbit.infrastructure.persistence.repository.RuleValidationRepository;
import io.github.stellorbit.infrastructure.persistence.repository.StellnulaPublishRecordRepository;
import io.github.stellorbit.interfaces.http.dto.ApprovalActionRequest;
import io.github.stellorbit.interfaces.http.dto.ApprovalResponse;
import io.github.stellorbit.interfaces.http.dto.BatchRuleEnabledResponse;
import io.github.stellorbit.interfaces.http.dto.PageResponse;
import io.github.stellorbit.interfaces.http.dto.ReleaseItemResponse;
import io.github.stellorbit.interfaces.http.dto.RuleReleaseDiffResponse;
import io.github.stellorbit.interfaces.http.dto.RuleReleaseImpactResponse;
import io.github.stellorbit.interfaces.http.dto.RuleReleaseItemDiffResponse;
import io.github.stellorbit.interfaces.http.dto.RuleReleaseResponse;
import io.github.stellorbit.interfaces.http.dto.RuleReleaseSummaryResponse;
import io.github.stellorbit.interfaces.http.dto.RuleSummaryResponse;
import io.github.stellorbit.interfaces.http.dto.RuleValidationResponse;
import io.github.stellorbit.interfaces.http.dto.StellnulaConfigDiffResponse;
import io.github.stellorbit.interfaces.http.dto.StellnulaPublishRecordResponse;
import io.github.stellorbit.interfaces.http.error.InvalidRuleRequestException;
import io.github.stellorbit.interfaces.http.error.ResourceNotFoundException;
import io.github.stellorbit.interfaces.http.security.ControlPlaneSecurityContextHolder;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ControlPlaneQueryUseCase {

  private static final String RESOURCE_RULE_RELEASE = "RULE_RELEASE";

  private final GovernanceRuleRepository governanceRuleRepository;
  private final RuleReleaseRepository ruleReleaseRepository;
  private final ReleaseItemRepository releaseItemRepository;
  private final StellnulaPublishRecordRepository stellnulaPublishRecordRepository;
  private final RuleValidationRepository ruleValidationRepository;
  private final AuditEventRepository auditEventRepository;

  public ControlPlaneQueryUseCase(
      GovernanceRuleRepository governanceRuleRepository,
      RuleReleaseRepository ruleReleaseRepository,
      ReleaseItemRepository releaseItemRepository,
      StellnulaPublishRecordRepository stellnulaPublishRecordRepository,
      RuleValidationRepository ruleValidationRepository,
      AuditEventRepository auditEventRepository) {
    this.governanceRuleRepository = governanceRuleRepository;
    this.ruleReleaseRepository = ruleReleaseRepository;
    this.releaseItemRepository = releaseItemRepository;
    this.stellnulaPublishRecordRepository = stellnulaPublishRecordRepository;
    this.ruleValidationRepository = ruleValidationRepository;
    this.auditEventRepository = auditEventRepository;
  }

  /** 分页搜索规则。 */
  @Transactional(readOnly = true)
  public PageResponse<RuleSummaryResponse> searchRules(
      UUID instanceSpaceId,
      UUID applicationId,
      String ruleType,
      String status,
      Boolean enabled,
      String keyword,
      int page,
      int size) {
    UUID scopedInstanceSpaceId = scopedInstanceSpaceId(instanceSpaceId);
    Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
    Page<GovernanceRuleEntity> result =
        governanceRuleRepository.findAll(
            ruleSpec(scopedInstanceSpaceId, applicationId, ruleType, status, enabled, keyword),
            pageable);
    return toPage(result.map(this::toRuleSummaryResponse));
  }

  /** 批量启用或停用规则。 */
  @Transactional
  public BatchRuleEnabledResponse batchSetEnabled(
      List<UUID> ruleIds, Boolean enabled, String operator) {
    List<GovernanceRuleEntity> rules = governanceRuleRepository.findAllById(ruleIds);
    if (rules.size() != ruleIds.stream().distinct().count()) {
      throw new InvalidRuleRequestException("部分规则不存在，无法批量启停");
    }
    for (GovernanceRuleEntity rule : rules) {
      ControlPlaneSecurityContextHolder.requireInstanceSpace(rule.getInstanceSpaceId());
      rule.setEnabled(enabled);
      rule.setStatus(Boolean.TRUE.equals(enabled) ? "DRAFT" : "DISABLED");
      rule.setUpdatedBy(operator);
    }
    governanceRuleRepository.saveAll(rules);
    return new BatchRuleEnabledResponse(
        enabled, rules.size(), rules.stream().map(GovernanceRuleEntity::getId).toList());
  }

  /** 分页查询发布列表。 */
  @Transactional(readOnly = true)
  public PageResponse<RuleReleaseSummaryResponse> searchReleases(
      UUID instanceSpaceId,
      UUID applicationId,
      String releaseStatus,
      String keyword,
      int page,
      int size) {
    UUID scopedInstanceSpaceId = scopedInstanceSpaceId(instanceSpaceId);
    Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    Page<RuleReleaseEntity> result =
        ruleReleaseRepository.findAll(
            releaseSpec(scopedInstanceSpaceId, applicationId, releaseStatus, keyword), pageable);
    return toPage(result.map(this::toReleaseSummaryResponse));
  }

  /** 查询发布详情。 */
  @Transactional(readOnly = true)
  public RuleReleaseResponse getRelease(UUID releaseId) {
    RuleReleaseEntity release = findRelease(releaseId);
    return toReleaseResponse(
        release,
        releaseItemRepository.findByReleaseId(releaseId),
        stellnulaPublishRecordRepository.findByReleaseIdOrderByCreatedAtAsc(releaseId));
  }

  /** 查询两个发布版本之间的差异。 */
  @Transactional(readOnly = true)
  public RuleReleaseDiffResponse diffReleases(UUID baseReleaseId, UUID targetReleaseId) {
    findRelease(baseReleaseId);
    findRelease(targetReleaseId);
    List<ReleaseItemEntity> baseItems = releaseItemRepository.findByReleaseId(baseReleaseId);
    List<ReleaseItemEntity> targetItems = releaseItemRepository.findByReleaseId(targetReleaseId);
    List<StellnulaPublishRecordEntity> baseRecords =
        stellnulaPublishRecordRepository.findByReleaseIdOrderByCreatedAtAsc(baseReleaseId);
    List<StellnulaPublishRecordEntity> targetRecords =
        stellnulaPublishRecordRepository.findByReleaseIdOrderByCreatedAtAsc(targetReleaseId);
    return new RuleReleaseDiffResponse(
        baseReleaseId,
        targetReleaseId,
        diffReleaseItems(baseItems, targetItems),
        diffPublishRecords(baseRecords, targetRecords));
  }

  /** 分析发布影响面。 */
  @Transactional(readOnly = true)
  public RuleReleaseImpactResponse analyzeImpact(UUID releaseId) {
    RuleReleaseEntity release = findRelease(releaseId);
    List<ReleaseItemEntity> items = releaseItemRepository.findByReleaseId(releaseId);
    List<StellnulaPublishRecordEntity> records =
        stellnulaPublishRecordRepository.findByReleaseIdOrderByCreatedAtAsc(releaseId);
    Map<String, Long> ruleTypeCounts =
        items.stream()
            .collect(
                Collectors.groupingBy(
                    ReleaseItemEntity::getRuleType, LinkedHashMap::new, Collectors.counting()));
    Map<String, Long> publishKindCounts =
        records.stream()
            .collect(
                Collectors.groupingBy(
                    StellnulaPublishRecordEntity::getPublishKind,
                    LinkedHashMap::new,
                    Collectors.counting()));
    return new RuleReleaseImpactResponse(
        release.getId(),
        release.getInstanceSpaceId(),
        release.getApplicationId(),
        items.size(),
        ruleTypeCounts,
        records.size(),
        publishKindCounts,
        records.stream().map(StellnulaPublishRecordEntity::getDataId).distinct().toList(),
        items.stream().map(ReleaseItemEntity::getRuleId).distinct().toList(),
        publishKindCounts.containsKey("AUTH_RULES"),
        publishKindCounts.containsKey("MTLS_CERTIFICATE"),
        publishKindCounts.containsKey("JWKS"),
        records.stream()
            .anyMatch(record -> metadataBoolean(record.getPayloadMetadata(), "sensitive")));
  }

  /** 分页查询规则校验记录。 */
  @Transactional(readOnly = true)
  public PageResponse<RuleValidationResponse> listValidations(UUID ruleId, int page, int size) {
    GovernanceRuleEntity rule =
        governanceRuleRepository
            .findById(ruleId)
            .orElseThrow(() -> new ResourceNotFoundException("GovernanceRule", ruleId));
    ControlPlaneSecurityContextHolder.requireInstanceSpace(rule.getInstanceSpaceId());
    Pageable pageable = PageRequest.of(page, size);
    return toPage(
        ruleValidationRepository
            .findByRuleIdOrderByValidatedAtDesc(ruleId, pageable)
            .map(this::toValidationResponse));
  }

  /** 提交发布审批。 */
  @Transactional
  public ApprovalResponse submitApproval(UUID releaseId, ApprovalActionRequest request) {
    RuleReleaseEntity release = findRelease(releaseId);
    return recordApprovalEvent(release, "RULE_RELEASE_APPROVAL_SUBMITTED", "PENDING", request);
  }

  /** 通过发布审批。 */
  @Transactional
  public ApprovalResponse approve(UUID releaseId, ApprovalActionRequest request) {
    RuleReleaseEntity release = findRelease(releaseId);
    validateApprover(release, request.operator());
    return recordApprovalEvent(release, "RULE_RELEASE_APPROVED", "APPROVED", request);
  }

  /** 驳回发布审批。 */
  @Transactional
  public ApprovalResponse reject(UUID releaseId, ApprovalActionRequest request) {
    RuleReleaseEntity release = findRelease(releaseId);
    validateApprover(release, request.operator());
    return recordApprovalEvent(release, "RULE_RELEASE_REJECTED", "REJECTED", request);
  }

  /** 查询发布审批时间线。 */
  @Transactional(readOnly = true)
  public List<ApprovalResponse> listApprovals(UUID releaseId) {
    findRelease(releaseId);
    return auditEventRepository
        .findByResourceTypeAndResourceIdOrderByCreatedAtDesc(RESOURCE_RULE_RELEASE, releaseId)
        .stream()
        .filter(
            event ->
                event.getEventType().startsWith("RULE_RELEASE_APPROVAL")
                    || "RULE_RELEASE_APPROVED".equals(event.getEventType())
                    || "RULE_RELEASE_REJECTED".equals(event.getEventType()))
        .map(this::toApprovalResponse)
        .toList();
  }

  private ApprovalResponse recordApprovalEvent(
      RuleReleaseEntity release,
      String eventType,
      String approvalStatus,
      ApprovalActionRequest request) {
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("approvalStatus", approvalStatus);
    detail.put("releaseVersion", release.getReleaseVersion());
    detail.put("releaseName", release.getReleaseName());
    detail.put("reason", request.reason());

    AuditEventEntity event = new AuditEventEntity();
    event.setEventType(eventType);
    event.setResourceType(RESOURCE_RULE_RELEASE);
    event.setResourceId(release.getId());
    event.setTenantId(ControlPlaneSecurityContextHolder.tenantId());
    event.setInstanceSpaceId(release.getInstanceSpaceId());
    event.setApplicationId(release.getApplicationId());
    event.setOperator(request.operator());
    event.setEventDetail(detail);
    event = auditEventRepository.saveAndFlush(event);
    return new ApprovalResponse(
        event.getId(),
        release.getId(),
        approvalStatus,
        request.operator(),
        request.reason(),
        detail,
        event.getCreatedAt());
  }

  private void validateApprover(RuleReleaseEntity release, String operator) {
    if (operator != null
        && (operator.equals(release.getCreatedBy()) || operator.equals(release.getPublishedBy()))) {
      throw new InvalidRuleRequestException("审批人不能和发布创建人或发布人相同");
    }
  }

  private ApprovalResponse toApprovalResponse(AuditEventEntity event) {
    Map<String, Object> detail = event.getEventDetail();
    Object releaseId = event.getResourceId();
    Object approvalStatus = detail == null ? null : detail.get("approvalStatus");
    Object reason = detail == null ? null : detail.get("reason");
    return new ApprovalResponse(
        event.getId(),
        releaseId instanceof UUID uuid ? uuid : event.getResourceId(),
        Objects.toString(approvalStatus, "UNKNOWN"),
        event.getOperator(),
        reason == null ? null : reason.toString(),
        detail,
        event.getCreatedAt());
  }

  private Specification<GovernanceRuleEntity> ruleSpec(
      UUID instanceSpaceId,
      UUID applicationId,
      String ruleType,
      String status,
      Boolean enabled,
      String keyword) {
    return (root, query, builder) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (instanceSpaceId != null) {
        predicates.add(builder.equal(root.get("instanceSpaceId"), instanceSpaceId));
      }
      if (applicationId != null) {
        predicates.add(builder.equal(root.get("applicationId"), applicationId));
      }
      if (hasText(ruleType)) {
        predicates.add(builder.equal(root.get("ruleType"), ruleType.trim().toUpperCase()));
      }
      if (hasText(status)) {
        predicates.add(builder.equal(root.get("status"), status.trim().toUpperCase()));
      }
      if (enabled != null) {
        predicates.add(builder.equal(root.get("enabled"), enabled));
      }
      if (hasText(keyword)) {
        String pattern = like(keyword);
        predicates.add(
            builder.or(
                builder.like(builder.lower(root.get("ruleCode")), pattern),
                builder.like(builder.lower(root.get("ruleName")), pattern),
                builder.like(builder.lower(root.get("description")), pattern)));
      }
      return builder.and(predicates.toArray(Predicate[]::new));
    };
  }

  private Specification<RuleReleaseEntity> releaseSpec(
      UUID instanceSpaceId, UUID applicationId, String releaseStatus, String keyword) {
    return (root, query, builder) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (instanceSpaceId != null) {
        predicates.add(builder.equal(root.get("instanceSpaceId"), instanceSpaceId));
      }
      if (applicationId != null) {
        predicates.add(builder.equal(root.get("applicationId"), applicationId));
      }
      if (hasText(releaseStatus)) {
        predicates.add(
            builder.equal(root.get("releaseStatus"), releaseStatus.trim().toUpperCase()));
      }
      if (hasText(keyword)) {
        String pattern = like(keyword);
        predicates.add(
            builder.or(
                builder.like(builder.lower(root.get("releaseName")), pattern),
                builder.like(builder.lower(root.get("releaseNote")), pattern),
                builder.like(builder.lower(root.get("idempotencyKey")), pattern)));
      }
      return builder.and(predicates.toArray(Predicate[]::new));
    };
  }

  private List<RuleReleaseItemDiffResponse> diffReleaseItems(
      List<ReleaseItemEntity> baseItems, List<ReleaseItemEntity> targetItems) {
    Map<UUID, ReleaseItemEntity> baseByRule = indexBy(baseItems, ReleaseItemEntity::getRuleId);
    Map<UUID, ReleaseItemEntity> targetByRule = indexBy(targetItems, ReleaseItemEntity::getRuleId);
    Set<UUID> ruleIds = union(baseByRule.keySet(), targetByRule.keySet());
    List<RuleReleaseItemDiffResponse> diffs = new ArrayList<>();
    for (UUID ruleId : ruleIds) {
      ReleaseItemEntity base = baseByRule.get(ruleId);
      ReleaseItemEntity target = targetByRule.get(ruleId);
      diffs.add(
          new RuleReleaseItemDiffResponse(
              ruleId,
              value(base, target, ReleaseItemEntity::getRuleCode),
              value(base, target, ReleaseItemEntity::getRuleName),
              value(base, target, ReleaseItemEntity::getRuleType),
              changeType(base, target),
              base == null ? null : base.getChecksum(),
              target == null ? null : target.getChecksum(),
              base == null ? null : base.getRuntimeSnapshotJson(),
              target == null ? null : target.getRuntimeSnapshotJson()));
    }
    return diffs;
  }

  private List<StellnulaConfigDiffResponse> diffPublishRecords(
      List<StellnulaPublishRecordEntity> baseRecords,
      List<StellnulaPublishRecordEntity> targetRecords) {
    Map<String, StellnulaPublishRecordEntity> baseByDataId =
        indexBy(baseRecords, StellnulaPublishRecordEntity::getDataId);
    Map<String, StellnulaPublishRecordEntity> targetByDataId =
        indexBy(targetRecords, StellnulaPublishRecordEntity::getDataId);
    Set<String> dataIds = union(baseByDataId.keySet(), targetByDataId.keySet());
    List<StellnulaConfigDiffResponse> diffs = new ArrayList<>();
    for (String dataId : dataIds) {
      StellnulaPublishRecordEntity base = baseByDataId.get(dataId);
      StellnulaPublishRecordEntity target = targetByDataId.get(dataId);
      diffs.add(
          new StellnulaConfigDiffResponse(
              dataId,
              value(base, target, StellnulaPublishRecordEntity::getPublishKind),
              changeType(base, target),
              base == null ? null : base.getChecksum(),
              target == null ? null : target.getChecksum(),
              base == null ? null : base.getPayloadMetadata(),
              target == null ? null : target.getPayloadMetadata()));
    }
    return diffs;
  }

  private RuleReleaseSummaryResponse toReleaseSummaryResponse(RuleReleaseEntity release) {
    List<ReleaseItemEntity> items = releaseItemRepository.findByReleaseId(release.getId());
    List<StellnulaPublishRecordEntity> records =
        stellnulaPublishRecordRepository.findByReleaseIdOrderByCreatedAtAsc(release.getId());
    int failedRecordCount =
        (int) records.stream().filter(record -> "FAILED".equals(record.getPublishStatus())).count();
    return new RuleReleaseSummaryResponse(
        release.getId(),
        release.getInstanceSpaceId(),
        release.getApplicationId(),
        release.getReleaseVersion(),
        release.getReleaseName(),
        release.getReleaseStatus(),
        release.getIdempotencyKey(),
        release.getChecksum(),
        release.getRollbackFromReleaseId(),
        release.getRetryCount(),
        release.getMaxRetryCount(),
        items.size(),
        records.size(),
        failedRecordCount,
        release.getFailureDetails(),
        release.getCreatedBy(),
        release.getPublishedBy(),
        release.getCreatedAt(),
        release.getPublishedAt(),
        release.getUpdatedAt());
  }

  private RuleReleaseResponse toReleaseResponse(
      RuleReleaseEntity release,
      List<ReleaseItemEntity> items,
      List<StellnulaPublishRecordEntity> publishRecords) {
    return new RuleReleaseResponse(
        release.getId(),
        release.getInstanceSpaceId(),
        release.getApplicationId(),
        release.getReleaseVersion(),
        release.getReleaseName(),
        release.getReleaseStatus(),
        release.getIdempotencyKey(),
        release.getSourceFormat(),
        release.getRuntimeFormat(),
        release.getChecksum(),
        release.getRollbackFromReleaseId(),
        release.getReleaseNote(),
        release.getRetryCount(),
        release.getMaxRetryCount(),
        release.getFailureDetails(),
        release.getRecoveryStatus(),
        release.getRecoveredBy(),
        release.getRecoveredAt(),
        release.getRecoveryNote(),
        release.getCreatedBy(),
        release.getPublishedBy(),
        release.getCreatedAt(),
        release.getPublishedAt(),
        release.getUpdatedAt(),
        items.stream()
            .sorted(Comparator.comparing(ReleaseItemEntity::getPriority))
            .map(this::toItemResponse)
            .toList(),
        publishRecords.stream().map(this::toPublishRecordResponse).toList());
  }

  private ReleaseItemResponse toItemResponse(ReleaseItemEntity entity) {
    return new ReleaseItemResponse(
        entity.getId(),
        entity.getReleaseId(),
        entity.getRuleId(),
        entity.getRuleType(),
        entity.getRuleCode(),
        entity.getRuleName(),
        entity.getDraftVersion(),
        entity.getPriority(),
        entity.getChecksum(),
        entity.getCreatedAt());
  }

  private StellnulaPublishRecordResponse toPublishRecordResponse(
      StellnulaPublishRecordEntity entity) {
    return new StellnulaPublishRecordResponse(
        entity.getId(),
        entity.getReleaseId(),
        entity.getPublishKind(),
        entity.getNamespaceCode(),
        entity.getConfigGroup(),
        entity.getConfigKey(),
        entity.getDataId(),
        entity.getContentType(),
        entity.getRuntimeFormat(),
        entity.getPayloadMetadata(),
        entity.getChecksum(),
        entity.getTargetVersion(),
        entity.getPublishStatus(),
        entity.getIdempotencyKey(),
        entity.getRetryCount(),
        entity.getMaxRetryCount(),
        entity.getNextRetryAt(),
        entity.getLastAttemptAt(),
        entity.getFailureDetails(),
        entity.getErrorMessage(),
        entity.getRecoveredBy(),
        entity.getRecoveredAt(),
        entity.getRecoveryNote(),
        entity.getPublishedAt(),
        entity.getCreatedAt());
  }

  private RuleSummaryResponse toRuleSummaryResponse(GovernanceRuleEntity entity) {
    return new RuleSummaryResponse(
        entity.getId(),
        entity.getInstanceSpaceId(),
        entity.getApplicationId(),
        entity.getRuleCode(),
        entity.getRuleName(),
        entity.getRuleType(),
        entity.getSourceFormat(),
        entity.getRuntimeFormat(),
        entity.getChecksum(),
        entity.getPriority(),
        entity.getEnabled(),
        entity.getStatus(),
        entity.getDraftVersion(),
        entity.getLatestReleaseId(),
        entity.getDescription(),
        entity.getTags(),
        entity.getCreatedBy(),
        entity.getUpdatedBy(),
        entity.getCreatedAt(),
        entity.getUpdatedAt(),
        entity.getPublishedAt());
  }

  private RuleValidationResponse toValidationResponse(RuleValidationEntity entity) {
    return new RuleValidationResponse(
        entity.getId(),
        entity.getRuleId(),
        entity.getDraftVersion(),
        entity.getSourceFormat(),
        entity.getValidationStatus(),
        entity.getNormalizedSnapshotJson(),
        entity.getErrorMessages(),
        entity.getWarningMessages(),
        entity.getValidatedBy(),
        entity.getValidatedAt());
  }

  private RuleReleaseEntity findRelease(UUID releaseId) {
    RuleReleaseEntity release =
        ruleReleaseRepository
            .findById(releaseId)
            .orElseThrow(() -> new ResourceNotFoundException("RuleRelease", releaseId));
    ControlPlaneSecurityContextHolder.requireInstanceSpace(release.getInstanceSpaceId());
    return release;
  }

  private UUID scopedInstanceSpaceId(UUID requestedInstanceSpaceId) {
    UUID allowedInstanceSpaceId = ControlPlaneSecurityContextHolder.requiredInstanceSpaceId();
    if (requestedInstanceSpaceId != null
        && !allowedInstanceSpaceId.equals(requestedInstanceSpaceId)) {
      throw new InvalidRuleRequestException("请求实例空间不在当前数据权限范围内");
    }
    return allowedInstanceSpaceId;
  }

  private <T> PageResponse<T> toPage(Page<T> page) {
    return new PageResponse<>(
        page.getContent(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }

  private <K, V> Map<K, V> indexBy(List<V> values, Function<V, K> keyExtractor) {
    return values.stream()
        .collect(
            Collectors.toMap(
                keyExtractor, Function.identity(), (left, right) -> left, LinkedHashMap::new));
  }

  private <T> Set<T> union(Set<T> left, Set<T> right) {
    Set<T> result = new LinkedHashSet<>();
    result.addAll(left);
    result.addAll(right);
    return result;
  }

  private String changeType(Object base, Object target) {
    if (base == null) {
      return "ADDED";
    }
    if (target == null) {
      return "REMOVED";
    }
    if (base instanceof ReleaseItemEntity baseItem
        && target instanceof ReleaseItemEntity targetItem) {
      return Objects.equals(baseItem.getChecksum(), targetItem.getChecksum())
          ? "UNCHANGED"
          : "MODIFIED";
    }
    if (base instanceof StellnulaPublishRecordEntity baseRecord
        && target instanceof StellnulaPublishRecordEntity targetRecord) {
      return Objects.equals(baseRecord.getChecksum(), targetRecord.getChecksum())
          ? "UNCHANGED"
          : "MODIFIED";
    }
    return "MODIFIED";
  }

  private <T, R> R value(T base, T target, Function<T, R> extractor) {
    if (target != null) {
      return extractor.apply(target);
    }
    return base == null ? null : extractor.apply(base);
  }

  private boolean metadataBoolean(Map<String, Object> metadata, String key) {
    Object value = metadata == null ? null : metadata.get(key);
    if (value instanceof Boolean booleanValue) {
      return booleanValue;
    }
    return value != null && Boolean.parseBoolean(value.toString());
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private String like(String value) {
    return "%" + value.trim().toLowerCase() + "%";
  }
}
