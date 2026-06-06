package io.github.stellorbit.application.usecase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.stellorbit.application.port.CompiledGovernanceRule;
import io.github.stellorbit.application.port.GovernanceRuleCompatibilityValidator;
import io.github.stellorbit.application.port.GovernanceRuleConflictDetector;
import io.github.stellorbit.application.port.GovernanceRuleContentCompiler;
import io.github.stellorbit.application.port.RuleSemanticCheckResult;
import io.github.stellorbit.application.port.StellnulaPublishClient;
import io.github.stellorbit.application.port.StellnulaPublishCommand;
import io.github.stellorbit.application.port.StellnulaPublishResult;
import io.github.stellorbit.infrastructure.persistence.entity.ApplicationEntity;
import io.github.stellorbit.infrastructure.persistence.entity.AuditEventEntity;
import io.github.stellorbit.infrastructure.persistence.entity.AuthRuleCertificateEntity;
import io.github.stellorbit.infrastructure.persistence.entity.GovernanceRuleEntity;
import io.github.stellorbit.infrastructure.persistence.entity.InstanceSpaceEntity;
import io.github.stellorbit.infrastructure.persistence.entity.MtlsCertificateEntity;
import io.github.stellorbit.infrastructure.persistence.entity.PublishJobEntity;
import io.github.stellorbit.infrastructure.persistence.entity.PublishLockEntity;
import io.github.stellorbit.infrastructure.persistence.entity.ReleaseItemEntity;
import io.github.stellorbit.infrastructure.persistence.entity.RuleReleaseEntity;
import io.github.stellorbit.infrastructure.persistence.entity.StellnulaPublishRecordEntity;
import io.github.stellorbit.infrastructure.persistence.repository.ApplicationRepository;
import io.github.stellorbit.infrastructure.persistence.repository.AuditEventRepository;
import io.github.stellorbit.infrastructure.persistence.repository.AuthRuleCertificateRepository;
import io.github.stellorbit.infrastructure.persistence.repository.GovernanceRuleRepository;
import io.github.stellorbit.infrastructure.persistence.repository.InstanceSpaceRepository;
import io.github.stellorbit.infrastructure.persistence.repository.MtlsCertificateRepository;
import io.github.stellorbit.infrastructure.persistence.repository.PublishJobRepository;
import io.github.stellorbit.infrastructure.persistence.repository.PublishLockRepository;
import io.github.stellorbit.infrastructure.persistence.repository.ReleaseItemRepository;
import io.github.stellorbit.infrastructure.persistence.repository.RuleReleaseRepository;
import io.github.stellorbit.infrastructure.persistence.repository.StellnulaPublishRecordRepository;
import io.github.stellorbit.interfaces.http.dto.PublishGovernanceRulesRequest;
import io.github.stellorbit.interfaces.http.dto.RecoverRuleReleaseRequest;
import io.github.stellorbit.interfaces.http.dto.ReleaseItemResponse;
import io.github.stellorbit.interfaces.http.dto.RetryRuleReleaseRequest;
import io.github.stellorbit.interfaces.http.dto.RollbackRuleReleaseRequest;
import io.github.stellorbit.interfaces.http.dto.RuleReleaseResponse;
import io.github.stellorbit.interfaces.http.dto.StellnulaPublishRecordResponse;
import io.github.stellorbit.interfaces.http.error.InvalidRuleRequestException;
import io.github.stellorbit.interfaces.http.error.ResourceNotFoundException;
import io.github.stellorbit.interfaces.http.security.ControlPlaneSecurityContextHolder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PublishGovernanceRulesUseCase {

  private static final Set<String> RUNTIME_FORMATS = Set.of("JSON", "PROTOBUF");
  private static final String GOVERNANCE_NAMESPACE = "governance";
  private static final String DEFAULT_CONFIG_GROUP = "service-governance";
  private static final String DEFAULT_SCOPE = "default";
  private static final int DEFAULT_MAX_RETRY_COUNT = 3;
  private static final long PUBLISH_LOCK_TTL_SECONDS = 300;

  private final GovernanceRuleRepository governanceRuleRepository;
  private final InstanceSpaceRepository instanceSpaceRepository;
  private final ApplicationRepository applicationRepository;
  private final RuleReleaseRepository ruleReleaseRepository;
  private final ReleaseItemRepository releaseItemRepository;
  private final StellnulaPublishRecordRepository stellnulaPublishRecordRepository;
  private final AuditEventRepository auditEventRepository;
  private final AuthRuleCertificateRepository authRuleCertificateRepository;
  private final MtlsCertificateRepository mtlsCertificateRepository;
  private final PublishJobRepository publishJobRepository;
  private final PublishLockRepository publishLockRepository;
  private final StellnulaPublishClient stellnulaPublishClient;
  private final GovernanceRuleContentCompiler governanceRuleContentCompiler;
  private final GovernanceRuleConflictDetector governanceRuleConflictDetector;
  private final GovernanceRuleCompatibilityValidator governanceRuleCompatibilityValidator;
  private final TransactionTemplate transactionTemplate;
  private final ObjectMapper objectMapper;

  public PublishGovernanceRulesUseCase(
      GovernanceRuleRepository governanceRuleRepository,
      InstanceSpaceRepository instanceSpaceRepository,
      ApplicationRepository applicationRepository,
      RuleReleaseRepository ruleReleaseRepository,
      ReleaseItemRepository releaseItemRepository,
      StellnulaPublishRecordRepository stellnulaPublishRecordRepository,
      AuditEventRepository auditEventRepository,
      AuthRuleCertificateRepository authRuleCertificateRepository,
      MtlsCertificateRepository mtlsCertificateRepository,
      PublishJobRepository publishJobRepository,
      PublishLockRepository publishLockRepository,
      StellnulaPublishClient stellnulaPublishClient,
      GovernanceRuleContentCompiler governanceRuleContentCompiler,
      GovernanceRuleConflictDetector governanceRuleConflictDetector,
      GovernanceRuleCompatibilityValidator governanceRuleCompatibilityValidator,
      TransactionTemplate transactionTemplate,
      ObjectMapper objectMapper) {
    this.governanceRuleRepository = governanceRuleRepository;
    this.instanceSpaceRepository = instanceSpaceRepository;
    this.applicationRepository = applicationRepository;
    this.ruleReleaseRepository = ruleReleaseRepository;
    this.releaseItemRepository = releaseItemRepository;
    this.stellnulaPublishRecordRepository = stellnulaPublishRecordRepository;
    this.auditEventRepository = auditEventRepository;
    this.authRuleCertificateRepository = authRuleCertificateRepository;
    this.mtlsCertificateRepository = mtlsCertificateRepository;
    this.publishJobRepository = publishJobRepository;
    this.publishLockRepository = publishLockRepository;
    this.stellnulaPublishClient = stellnulaPublishClient;
    this.governanceRuleContentCompiler = governanceRuleContentCompiler;
    this.governanceRuleConflictDetector = governanceRuleConflictDetector;
    this.governanceRuleCompatibilityValidator = governanceRuleCompatibilityValidator;
    this.transactionTemplate = transactionTemplate;
    this.objectMapper = objectMapper;
  }

  /** 创建发布计划并执行一次发布推进。 */
  public RuleReleaseResponse publish(PublishGovernanceRulesRequest request) {
    ReleaseExecutionPlan plan = transactionTemplate.execute(status -> createReleasePlan(request));
    if (plan == null) {
      throw new IllegalStateException("发布计划创建失败");
    }
    return loadReleaseResponse(plan.releaseId());
  }

  /** 重试失败或待处理的发布项。 */
  public RuleReleaseResponse retry(UUID releaseId, RetryRuleReleaseRequest request) {
    ReleaseExecutionPlan plan =
        transactionTemplate.execute(status -> prepareRetry(releaseId, request));
    if (plan == null) {
      throw new IllegalStateException("发布重试准备失败");
    }
    return loadReleaseResponse(plan.releaseId());
  }

  /** 重试单条stellnula发布记录。 */
  public RuleReleaseResponse retryPublishRecord(
      UUID releaseId, UUID publishRecordId, RetryRuleReleaseRequest request) {
    ReleaseExecutionPlan plan =
        transactionTemplate.execute(
            status -> preparePublishRecordRetry(releaseId, publishRecordId, request));
    if (plan == null) {
      throw new IllegalStateException("发布记录重试准备失败");
    }
    return loadReleaseResponse(releaseId);
  }

  /** 人工恢复发布状态。 */
  public RuleReleaseResponse recover(UUID releaseId, RecoverRuleReleaseRequest request) {
    transactionTemplate.executeWithoutResult(status -> recoverManually(releaseId, request));
    return loadReleaseResponse(releaseId);
  }

  /** 按历史发布版本回滚，并将旧版本内容重新发布到配置中心。 */
  public RuleReleaseResponse rollback(
      UUID rollbackFromReleaseId, RollbackRuleReleaseRequest request) {
    ReleaseExecutionPlan plan =
        transactionTemplate.execute(status -> createRollbackPlan(rollbackFromReleaseId, request));
    if (plan == null) {
      throw new IllegalStateException("回滚发布计划创建失败");
    }
    return loadReleaseResponse(plan.releaseId());
  }

  private ReleaseExecutionPlan createReleasePlan(PublishGovernanceRulesRequest request) {
    ControlPlaneSecurityContextHolder.requireInstanceSpace(request.instanceSpaceId());
    String runtimeFormat = normalizeRuntimeFormat(request.runtimeFormat());
    String idempotencyKey = idempotencyKey(request);
    int maxRetryCount = maxRetryCount(request.maxRetryCount());
    InstanceSpaceEntity instanceSpace = findInstanceSpace(request.instanceSpaceId());
    ApplicationEntity application = findApplication(request.applicationId());
    validateApplicationScope(request.instanceSpaceId(), application);

    RuleReleaseEntity existingRelease =
        ruleReleaseRepository
            .findByInstanceSpaceIdAndApplicationIdAndIdempotencyKey(
                request.instanceSpaceId(), request.applicationId(), idempotencyKey)
            .orElse(null);
    if (existingRelease != null) {
      return new ReleaseExecutionPlan(
          existingRelease.getId(), false, request.publishedBy(), "幂等命中");
    }
    if (ruleReleaseRepository.existsByInstanceSpaceIdAndApplicationIdAndReleaseVersion(
        request.instanceSpaceId(), request.applicationId(), request.releaseVersion())) {
      throw new InvalidRuleRequestException("发布版本已存在");
    }

    RuleReleaseEntity release =
        createRelease(request, runtimeFormat, idempotencyKey, maxRetryCount);
    release = ruleReleaseRepository.saveAndFlush(release);
    acquirePublishLock(release, request.publishedBy());
    transitionRelease(release, "VALIDATING");

    try {
      List<GovernanceRuleEntity> rules = findRules(request);
      validateRuleSelection(request, rules);
      List<CompiledGovernanceRule> compiledRules = compileRules(rules, application);
      validateSemanticChecks(request, compiledRules);
      List<Map<String, Object>> itemSnapshots = buildItemSnapshots(compiledRules);
      Map<String, Object> releaseSnapshot =
          buildReleaseSnapshot(request, runtimeFormat, itemSnapshots, application);
      String releasePayload = toJson(releaseSnapshot);
      setReleasePayload(release, runtimeFormat, releaseSnapshot, releasePayload);
      release.setChecksum(sha256(releasePayload));

      List<ReleaseItemEntity> items =
          createReleaseItems(release.getId(), compiledRules, runtimeFormat);
      releaseItemRepository.saveAllAndFlush(items);

      String configGroup = normalizeConfigGroup(request.configGroup());
      List<StellnulaPublishRecordEntity> publishRecords =
          createPublishRecords(
              release,
              request,
              application,
              instanceSpace,
              configGroup,
              idempotencyKey,
              compiledRules);
      stellnulaPublishRecordRepository.saveAllAndFlush(publishRecords);
      enqueuePublishJobs(
          release,
          publishRecords,
          request.publishedBy(),
          defaultText(request.releaseNote(), "publish from stellorbit-service"),
          OffsetDateTime.now());
      transitionRelease(release, "PUBLISHING");
      return new ReleaseExecutionPlan(
          release.getId(),
          true,
          request.publishedBy(),
          defaultText(request.releaseNote(), "publish from stellorbit-service"));
    } catch (RuntimeException exception) {
      release.setReleaseStatus("FAILED");
      release.setFailureDetails(
          appendFailure(release.getFailureDetails(), "VALIDATING", exception));
      ruleReleaseRepository.saveAndFlush(release);
      releasePublishLock(release.getId(), "VALIDATING_FAILED", OffsetDateTime.now());
      return new ReleaseExecutionPlan(
          release.getId(), false, request.publishedBy(), exception.getMessage());
    }
  }

  private ReleaseExecutionPlan prepareRetry(UUID releaseId, RetryRuleReleaseRequest request) {
    RuleReleaseEntity release = findRelease(releaseId);
    if ("PUBLISHED".equals(release.getReleaseStatus())) {
      return new ReleaseExecutionPlan(releaseId, false, request.operator(), "发布已完成，无需重试");
    }
    int maxRetryCount =
        request.maxRetryCount() == null
            ? defaultInt(release.getMaxRetryCount(), DEFAULT_MAX_RETRY_COUNT)
            : maxRetryCount(request.maxRetryCount());
    release.setMaxRetryCount(maxRetryCount);
    release.setRetryCount(defaultInt(release.getRetryCount(), 0) + 1);
    release.setReleaseStatus("PUBLISHING");
    ruleReleaseRepository.saveAndFlush(release);
    acquirePublishLock(release, request.operator());

    List<StellnulaPublishRecordEntity> records =
        stellnulaPublishRecordRepository.findByReleaseIdOrderByCreatedAtAsc(releaseId);
    boolean hasRetryable = false;
    for (StellnulaPublishRecordEntity record : records) {
      if ("PUBLISHED".equals(record.getPublishStatus())) {
        continue;
      }
      if (defaultInt(record.getRetryCount(), 0) >= maxRetryCount) {
        record.setPublishStatus("FAILED");
        record.setErrorMessage("发布项已达到最大重试次数");
        continue;
      }
      hasRetryable = true;
      record.setMaxRetryCount(maxRetryCount);
      record.setPublishStatus("PENDING");
      record.setNextRetryAt(null);
      record.setErrorMessage(null);
    }
    stellnulaPublishRecordRepository.saveAllAndFlush(records);
    enqueuePublishJobs(
        release,
        records.stream().filter(record -> !"PUBLISHED".equals(record.getPublishStatus())).toList(),
        request.operator(),
        defaultText(request.reason(), "manual retry"),
        OffsetDateTime.now());
    if (!hasRetryable) {
      updateReleaseFinalState(release, records, OffsetDateTime.now());
    }
    return new ReleaseExecutionPlan(
        releaseId, hasRetryable, request.operator(), defaultText(request.reason(), "manual retry"));
  }

  private ReleaseExecutionPlan preparePublishRecordRetry(
      UUID releaseId, UUID publishRecordId, RetryRuleReleaseRequest request) {
    RuleReleaseEntity release = findRelease(releaseId);
    StellnulaPublishRecordEntity record =
        stellnulaPublishRecordRepository
            .findByIdAndReleaseId(publishRecordId, releaseId)
            .orElseThrow(
                () -> new ResourceNotFoundException("StellnulaPublishRecord", publishRecordId));
    if ("PUBLISHED".equals(record.getPublishStatus())) {
      return new ReleaseExecutionPlan(releaseId, false, request.operator(), "发布记录已完成，无需重试");
    }
    int maxRetryCount =
        request.maxRetryCount() == null
            ? defaultInt(record.getMaxRetryCount(), DEFAULT_MAX_RETRY_COUNT)
            : maxRetryCount(request.maxRetryCount());
    if (defaultInt(record.getRetryCount(), 0) >= maxRetryCount) {
      throw new InvalidRuleRequestException("发布记录已达到最大重试次数");
    }
    release.setReleaseStatus("PUBLISHING");
    release.setRetryCount(defaultInt(release.getRetryCount(), 0) + 1);
    release.setMaxRetryCount(Math.max(defaultInt(release.getMaxRetryCount(), 0), maxRetryCount));
    ruleReleaseRepository.saveAndFlush(release);
    acquirePublishLock(release, request.operator());

    record.setPublishStatus("PENDING");
    record.setMaxRetryCount(maxRetryCount);
    record.setNextRetryAt(null);
    record.setErrorMessage(null);
    stellnulaPublishRecordRepository.saveAndFlush(record);
    enqueuePublishJobs(
        release,
        List.of(record),
        request.operator(),
        defaultText(request.reason(), "manual record retry"),
        OffsetDateTime.now());
    return new ReleaseExecutionPlan(
        releaseId, true, request.operator(), defaultText(request.reason(), "manual record retry"));
  }

  private ReleaseExecutionPlan createRollbackPlan(
      UUID rollbackFromReleaseId, RollbackRuleReleaseRequest request) {
    RuleReleaseEntity sourceRelease = findRelease(rollbackFromReleaseId);
    if (!"PUBLISHED".equals(sourceRelease.getReleaseStatus())) {
      throw new InvalidRuleRequestException("只能回滚到已发布成功的release");
    }
    InstanceSpaceEntity instanceSpace = findInstanceSpace(sourceRelease.getInstanceSpaceId());
    ApplicationEntity application = findApplication(sourceRelease.getApplicationId());
    validateApplicationScope(sourceRelease.getInstanceSpaceId(), application);

    List<ReleaseItemEntity> sourceItems =
        releaseItemRepository.findByReleaseId(sourceRelease.getId());
    List<StellnulaPublishRecordEntity> sourceRecords =
        stellnulaPublishRecordRepository.findByReleaseIdOrderByCreatedAtAsc(sourceRelease.getId());
    if (sourceItems.isEmpty() || sourceRecords.isEmpty()) {
      throw new InvalidRuleRequestException("源release缺少可回滚的发布快照");
    }

    Long releaseVersion =
        request.releaseVersion() == null
            ? nextReleaseVersion(
                sourceRelease.getInstanceSpaceId(), sourceRelease.getApplicationId())
            : request.releaseVersion();
    String idempotencyKey = rollbackIdempotencyKey(sourceRelease.getId(), releaseVersion, request);
    RuleReleaseEntity existingRelease =
        ruleReleaseRepository
            .findByInstanceSpaceIdAndApplicationIdAndIdempotencyKey(
                sourceRelease.getInstanceSpaceId(),
                sourceRelease.getApplicationId(),
                idempotencyKey)
            .orElse(null);
    if (existingRelease != null) {
      return new ReleaseExecutionPlan(existingRelease.getId(), false, request.operator(), "回滚幂等命中");
    }
    if (ruleReleaseRepository.existsByInstanceSpaceIdAndApplicationIdAndReleaseVersion(
        sourceRelease.getInstanceSpaceId(), sourceRelease.getApplicationId(), releaseVersion)) {
      throw new InvalidRuleRequestException("回滚发布版本已存在");
    }

    String reason = defaultText(request.reason(), "rollback to release " + sourceRelease.getId());
    String releaseName =
        defaultText(
            request.releaseName(), "Rollback to release " + sourceRelease.getReleaseVersion());
    int maxRetryCount = maxRetryCount(request.maxRetryCount());
    RuleReleaseEntity rollbackRelease =
        createRollbackRelease(
            sourceRelease, releaseVersion, releaseName, idempotencyKey, maxRetryCount, request);
    rollbackRelease = ruleReleaseRepository.saveAndFlush(rollbackRelease);
    acquirePublishLock(rollbackRelease, request.operator());
    transitionRelease(rollbackRelease, "VALIDATING");

    List<ReleaseItemEntity> rollbackItems = cloneReleaseItems(rollbackRelease.getId(), sourceItems);
    releaseItemRepository.saveAllAndFlush(rollbackItems);
    String configGroup =
        request.configGroup() == null || request.configGroup().isBlank()
            ? null
            : normalizeConfigGroup(request.configGroup());
    List<StellnulaPublishRecordEntity> rollbackRecords =
        clonePublishRecords(
            rollbackRelease,
            sourceRecords,
            application,
            instanceSpace,
            configGroup,
            idempotencyKey,
            releaseName,
            reason,
            request.operator());
    stellnulaPublishRecordRepository.saveAllAndFlush(rollbackRecords);
    enqueuePublishJobs(
        rollbackRelease, rollbackRecords, request.operator(), reason, OffsetDateTime.now());
    recordRollbackAudit(sourceRelease, rollbackRelease, request, releaseVersion, reason);
    transitionRelease(rollbackRelease, "PUBLISHING");
    return new ReleaseExecutionPlan(rollbackRelease.getId(), true, request.operator(), reason);
  }

  private void recoverManually(UUID releaseId, RecoverRuleReleaseRequest request) {
    RuleReleaseEntity release = findRelease(releaseId);
    OffsetDateTime now = OffsetDateTime.now();
    boolean markFailedRecordsAsPublished =
        request.markFailedRecordsAsPublished() == null
            || Boolean.TRUE.equals(request.markFailedRecordsAsPublished());

    List<StellnulaPublishRecordEntity> records =
        stellnulaPublishRecordRepository.findByReleaseIdOrderByCreatedAtAsc(releaseId);
    if (markFailedRecordsAsPublished) {
      for (StellnulaPublishRecordEntity record : records) {
        if (!"PUBLISHED".equals(record.getPublishStatus())) {
          record.setPublishStatus("PUBLISHED");
          record.setTargetVersion(defaultText(record.getTargetVersion(), "manual-" + releaseId));
          record.setErrorMessage(null);
          record.setRecoveredBy(request.operator());
          record.setRecoveredAt(now);
          record.setRecoveryNote(request.recoveryNote());
          record.setPublishedAt(now);
          record.setPayloadMetadata(
              withManualRecovery(
                  record.getPayloadMetadata(), request.operator(), request.recoveryNote(), now));
        }
      }
      stellnulaPublishRecordRepository.saveAllAndFlush(records);
    }

    release.setRecoveryStatus("MANUAL_RECOVERED");
    release.setRecoveredBy(request.operator());
    release.setRecoveredAt(now);
    release.setRecoveryNote(request.recoveryNote());
    updateReleaseFinalState(release, records, now);
  }

  /** 由发布worker执行单个发布任务。 */
  public void executePublishJob(UUID jobId, String workerId) {
    PublishJobEntity job = claimPublishJob(jobId, workerId);
    if (job == null) {
      return;
    }
    try {
      switch (job.getJobType()) {
        case "PUBLISH_RECORD" -> executePublishRecordJob(job, workerId);
        case "RECONCILE_RECORD" -> executeReconcileRecordJob(job, workerId);
        case "COMPENSATE_RELEASE" -> executeCompensateReleaseJob(job, workerId);
        default -> failJob(job, "不支持的发布任务类型: " + job.getJobType(), OffsetDateTime.now());
      }
    } catch (RuntimeException exception) {
      failJob(job, exception.getMessage(), OffsetDateTime.now());
    }
  }

  /** 恢复长时间处于PUBLISHING但没有可运行任务的发布。 */
  public void recoverStuckRelease(UUID releaseId, String workerId) {
    RuleReleaseEntity release = findReleaseForWorker(releaseId);
    List<StellnulaPublishRecordEntity> records =
        stellnulaPublishRecordRepository.findByReleaseIdOrderByCreatedAtAsc(releaseId);
    OffsetDateTime now = OffsetDateTime.now();
    boolean hasRetryable = false;
    List<StellnulaPublishRecordEntity> publishRetryRecords = new ArrayList<>();
    for (StellnulaPublishRecordEntity record : records) {
      if ("PUBLISHED".equals(record.getPublishStatus())) {
        continue;
      }
      if ("PUBLISHING".equals(record.getPublishStatus())) {
        enqueueReconcileJob(release, record, workerId, "stuck publish reconciliation", now);
        hasRetryable = true;
        continue;
      }
      if (defaultInt(record.getRetryCount(), 0)
          < defaultInt(record.getMaxRetryCount(), DEFAULT_MAX_RETRY_COUNT)) {
        record.setPublishStatus("PENDING");
        record.setNextRetryAt(now);
        publishRetryRecords.add(record);
        hasRetryable = true;
      }
    }
    stellnulaPublishRecordRepository.saveAllAndFlush(records);
    if (hasRetryable) {
      acquirePublishLock(release, workerId);
      enqueuePublishJobs(release, publishRetryRecords, workerId, "stuck release recovery", now);
      enqueueCompensationJob(release, workerId, "stuck release compensation", now.plusSeconds(10));
    } else {
      updateReleaseFinalState(release, records, now);
    }
  }

  private PublishJobEntity claimPublishJob(UUID jobId, String workerId) {
    OffsetDateTime now = OffsetDateTime.now();
    PublishJobEntity job = publishJobRepository.findById(jobId).orElse(null);
    if (job == null || !isRunnableJob(job, now)) {
      return null;
    }
    job.setJobStatus("RUNNING");
    job.setLockedBy(workerId);
    job.setLockedAt(now);
    job.setLastAttemptAt(now);
    job.setAttemptCount(defaultInt(job.getAttemptCount(), 0) + 1);
    job.setErrorMessage(null);
    return publishJobRepository.saveAndFlush(job);
  }

  private boolean isRunnableJob(PublishJobEntity job, OffsetDateTime now) {
    boolean runnableStatus =
        "PENDING".equals(job.getJobStatus()) || "FAILED".equals(job.getJobStatus());
    return runnableStatus && !job.getNextRunAt().isAfter(now);
  }

  private void executePublishRecordJob(PublishJobEntity job, String workerId) {
    if (job.getPublishRecordId() == null) {
      failJob(job, "发布记录ID不能为空", OffsetDateTime.now());
      return;
    }
    StellnulaPublishRecordEntity record =
        stellnulaPublishRecordRepository
            .findByIdAndReleaseId(job.getPublishRecordId(), job.getReleaseId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "StellnulaPublishRecord", job.getPublishRecordId()));
    RuleReleaseEntity release = findReleaseForWorker(job.getReleaseId());
    if ("PUBLISHED".equals(record.getPublishStatus())) {
      succeedJob(job, OffsetDateTime.now());
      updateReleaseFinalState(
          release,
          stellnulaPublishRecordRepository.findByReleaseIdOrderByCreatedAtAsc(job.getReleaseId()),
          OffsetDateTime.now());
      return;
    }
    ApplicationEntity application = findApplication(release.getApplicationId());
    InstanceSpaceEntity instanceSpace = findInstanceSpace(release.getInstanceSpaceId());
    String reason = metadataText(job, "reason", "async publish");
    String operator = metadataText(job, "operator", workerId);
    OffsetDateTime now = OffsetDateTime.now();
    publishSingleRecord(record, application, instanceSpace, operator, reason, now);
    StellnulaPublishRecordEntity updatedRecord =
        stellnulaPublishRecordRepository.findById(record.getId()).orElse(record);
    if ("PUBLISHED".equals(updatedRecord.getPublishStatus())) {
      succeedJob(job, now);
    } else {
      retryOrFailJob(
          job, updatedRecord.getErrorMessage(), nextBackoffAt(job, updatedRecord, now), now);
    }
    updateReleaseFinalState(
        release,
        stellnulaPublishRecordRepository.findByReleaseIdOrderByCreatedAtAsc(job.getReleaseId()),
        now);
  }

  private void executeReconcileRecordJob(PublishJobEntity job, String workerId) {
    if (job.getPublishRecordId() == null) {
      failJob(job, "发布记录ID不能为空", OffsetDateTime.now());
      return;
    }
    StellnulaPublishRecordEntity record =
        stellnulaPublishRecordRepository
            .findByIdAndReleaseId(job.getPublishRecordId(), job.getReleaseId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "StellnulaPublishRecord", job.getPublishRecordId()));
    RuleReleaseEntity release = findReleaseForWorker(job.getReleaseId());
    ApplicationEntity application = findApplication(release.getApplicationId());
    InstanceSpaceEntity instanceSpace = findInstanceSpace(release.getInstanceSpaceId());
    StellnulaPublishResult result =
        stellnulaPublishClient.query(
            toPublishCommand(
                record,
                application,
                instanceSpace,
                metadataText(job, "operator", workerId),
                metadataText(job, "reason", "async reconcile")));
    OffsetDateTime now = OffsetDateTime.now();
    if (result.success()) {
      markRecordPublishedByQuery(record, result, now);
      succeedJob(job, now);
    } else {
      retryOrFailJob(job, result.errorMessage(), nextBackoffAt(job, record, now), now);
    }
    updateReleaseFinalState(
        release,
        stellnulaPublishRecordRepository.findByReleaseIdOrderByCreatedAtAsc(job.getReleaseId()),
        now);
  }

  private void executeCompensateReleaseJob(PublishJobEntity job, String workerId) {
    RuleReleaseEntity release = findReleaseForWorker(job.getReleaseId());
    List<StellnulaPublishRecordEntity> records =
        stellnulaPublishRecordRepository.findByReleaseIdOrderByCreatedAtAsc(job.getReleaseId());
    OffsetDateTime now = OffsetDateTime.now();
    List<StellnulaPublishRecordEntity> retryable =
        records.stream().filter(record -> !"PUBLISHED".equals(record.getPublishStatus())).toList();
    enqueuePublishJobs(
        release,
        retryable,
        metadataText(job, "operator", workerId),
        metadataText(job, "reason", "async compensation"),
        now);
    succeedJob(job, now);
  }

  private void publishSingleRecord(
      StellnulaPublishRecordEntity record,
      ApplicationEntity application,
      InstanceSpaceEntity instanceSpace,
      String operator,
      String reason,
      OffsetDateTime attemptAt) {
    record.setPublishStatus("PUBLISHING");
    record.setLastAttemptAt(attemptAt);
    record.setRetryCount(defaultInt(record.getRetryCount(), 0) + 1);
    stellnulaPublishRecordRepository.saveAndFlush(record);

    StellnulaPublishResult result =
        stellnulaPublishClient.publish(
            toPublishCommand(record, application, instanceSpace, operator, reason));

    if (result.success()) {
      record.setPublishStatus("PUBLISHED");
      record.setTargetVersion(result.releaseNo());
      record.setPayloadMetadata(
          withStellnulaResult(record.getPayloadMetadata(), result, record.getDataId()));
      record.setChecksum(defaultText(result.checksum(), record.getChecksum()));
      record.setErrorMessage(null);
      record.setNextRetryAt(null);
      record.setPublishedAt(attemptAt);
      stellnulaPublishRecordRepository.saveAndFlush(record);
      return;
    }

    record.setPublishStatus("FAILED");
    record.setErrorMessage(result.errorMessage());
    record.setFailureDetails(
        appendFailure(record.getFailureDetails(), "PUBLISHING", result.errorMessage()));
    if (defaultInt(record.getRetryCount(), 0)
        < defaultInt(record.getMaxRetryCount(), DEFAULT_MAX_RETRY_COUNT)) {
      record.setNextRetryAt(attemptAt.plusSeconds(30));
    }
    stellnulaPublishRecordRepository.saveAndFlush(record);
  }

  private StellnulaPublishCommand toPublishCommand(
      StellnulaPublishRecordEntity record,
      ApplicationEntity application,
      InstanceSpaceEntity instanceSpace,
      String operator,
      String reason) {
    return new StellnulaPublishCommand(
        record.getDataId(),
        ruleNameFrom(record),
        application.getApplicationCode(),
        "APPLICATION",
        record.getNamespaceCode(),
        record.getConfigGroup(),
        record.getPublishKind(),
        metadataText(record, "env", instanceSpace.getEnvironment()),
        metadataText(record, "region", DEFAULT_SCOPE),
        metadataText(record, "zone", DEFAULT_SCOPE),
        metadataText(record, "cluster", DEFAULT_SCOPE),
        metadataText(record, "scopeMode", "INHERITABLE"),
        "FILE",
        metadataBoolean(record, "sensitive", false),
        metadataText(record, "releaseName", record.getDataId()),
        record.getPayloadText(),
        defaultText(reason, metadataText(record, "releaseNote", "publish from stellorbit-service")),
        operator,
        record.getPayloadMetadata(),
        record.getChecksum());
  }

  private void updateReleaseFinalState(
      RuleReleaseEntity release, List<StellnulaPublishRecordEntity> records, OffsetDateTime now) {
    long publishedCount =
        records.stream().filter(record -> "PUBLISHED".equals(record.getPublishStatus())).count();
    long failedCount =
        records.stream().filter(record -> "FAILED".equals(record.getPublishStatus())).count();
    if (!records.isEmpty() && publishedCount == records.size()) {
      release.setReleaseStatus("PUBLISHED");
      release.setPublishedAt(defaultTime(release.getPublishedAt(), now));
      markRulesPublished(release.getId(), now);
    } else if (publishedCount > 0 && failedCount > 0) {
      release.setReleaseStatus("PARTIAL_PUBLISHED");
    } else if (failedCount > 0) {
      release.setReleaseStatus("FAILED");
    } else {
      release.setReleaseStatus("PUBLISHING");
    }
    release.setFailureDetails(collectFailureDetails(records));
    ruleReleaseRepository.saveAndFlush(release);
    if (!"PUBLISHING".equals(release.getReleaseStatus())
        && !"VALIDATING".equals(release.getReleaseStatus())) {
      releasePublishLock(release.getId(), release.getReleaseStatus(), now);
    }
  }

  private void acquirePublishLock(RuleReleaseEntity release, String operator) {
    OffsetDateTime now = OffsetDateTime.now();
    String lockKey = publishLockKey(release.getInstanceSpaceId(), release.getApplicationId());
    PublishLockEntity existing =
        publishLockRepository.findByLockKeyAndLockStatus(lockKey, "ACTIVE").orElse(null);
    if (existing != null) {
      if (existing.getExpiresAt().isBefore(now)) {
        existing.setLockStatus("EXPIRED");
        existing.setReleasedAt(now);
        existing.setReleaseReason("expired before new release lock");
        publishLockRepository.saveAndFlush(existing);
      } else if (!existing.getReleaseId().equals(release.getId())) {
        throw new InvalidRuleRequestException("当前应用已有发布正在进行: " + existing.getReleaseId());
      } else {
        existing.setHeartbeatAt(now);
        existing.setExpiresAt(now.plusSeconds(PUBLISH_LOCK_TTL_SECONDS));
        publishLockRepository.saveAndFlush(existing);
        return;
      }
    }
    PublishLockEntity lock = new PublishLockEntity();
    lock.setInstanceSpaceId(release.getInstanceSpaceId());
    lock.setApplicationId(release.getApplicationId());
    lock.setReleaseId(release.getId());
    lock.setLockKey(lockKey);
    lock.setLockStatus("ACTIVE");
    lock.setLockedBy(defaultText(operator, "system"));
    lock.setLockedAt(now);
    lock.setHeartbeatAt(now);
    lock.setExpiresAt(now.plusSeconds(PUBLISH_LOCK_TTL_SECONDS));
    try {
      publishLockRepository.saveAndFlush(lock);
    } catch (DataIntegrityViolationException exception) {
      throw new InvalidRuleRequestException("当前应用已有发布正在进行");
    }
  }

  private void releasePublishLock(UUID releaseId, String reason, OffsetDateTime now) {
    publishLockRepository
        .findByReleaseIdAndLockStatus(releaseId, "ACTIVE")
        .ifPresent(
            lock -> {
              lock.setLockStatus("RELEASED");
              lock.setReleasedAt(now);
              lock.setReleaseReason(reason);
              publishLockRepository.saveAndFlush(lock);
            });
  }

  private void enqueuePublishJobs(
      RuleReleaseEntity release,
      List<StellnulaPublishRecordEntity> records,
      String operator,
      String reason,
      OffsetDateTime nextRunAt) {
    for (StellnulaPublishRecordEntity record : records) {
      if ("PUBLISHED".equals(record.getPublishStatus())) {
        continue;
      }
      upsertPublishJob(
          release,
          record,
          "PUBLISH_RECORD",
          "publish-record-" + record.getId(),
          operator,
          reason,
          defaultTime(record.getNextRetryAt(), nextRunAt));
    }
  }

  private void enqueueReconcileJob(
      RuleReleaseEntity release,
      StellnulaPublishRecordEntity record,
      String operator,
      String reason,
      OffsetDateTime nextRunAt) {
    upsertPublishJob(
        release,
        record,
        "RECONCILE_RECORD",
        "reconcile-record-" + record.getId(),
        operator,
        reason,
        nextRunAt);
  }

  private void enqueueCompensationJob(
      RuleReleaseEntity release, String operator, String reason, OffsetDateTime nextRunAt) {
    upsertPublishJob(
        release,
        null,
        "COMPENSATE_RELEASE",
        "compensate-release-" + release.getId(),
        operator,
        reason,
        nextRunAt);
  }

  private void upsertPublishJob(
      RuleReleaseEntity release,
      StellnulaPublishRecordEntity record,
      String jobType,
      String idempotencyKey,
      String operator,
      String reason,
      OffsetDateTime nextRunAt) {
    PublishJobEntity job = publishJobRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
    if (job == null) {
      job = new PublishJobEntity();
      job.setReleaseId(release.getId());
      job.setPublishRecordId(record == null ? null : record.getId());
      job.setInstanceSpaceId(release.getInstanceSpaceId());
      job.setApplicationId(release.getApplicationId());
      job.setJobType(jobType);
      job.setIdempotencyKey(idempotencyKey);
    } else if ("SUCCEEDED".equals(job.getJobStatus())) {
      return;
    }
    job.setJobStatus("PENDING");
    job.setMaxAttempts(
        record == null
            ? defaultInt(release.getMaxRetryCount(), DEFAULT_MAX_RETRY_COUNT)
            : defaultInt(record.getMaxRetryCount(), DEFAULT_MAX_RETRY_COUNT));
    job.setNextRunAt(nextRunAt);
    job.setLockedBy(null);
    job.setLockedAt(null);
    job.setCompletedAt(null);
    job.setErrorMessage(null);
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("operator", defaultText(operator, "system"));
    metadata.put("reason", defaultText(reason, "async publish"));
    job.setPayloadMetadata(metadata);
    publishJobRepository.saveAndFlush(job);
  }

  private void succeedJob(PublishJobEntity job, OffsetDateTime now) {
    job.setJobStatus("SUCCEEDED");
    job.setCompletedAt(now);
    job.setErrorMessage(null);
    job.setLockedBy(null);
    job.setLockedAt(null);
    publishJobRepository.saveAndFlush(job);
  }

  private void retryOrFailJob(
      PublishJobEntity job, String message, OffsetDateTime nextRunAt, OffsetDateTime now) {
    if (defaultInt(job.getAttemptCount(), 0)
        < defaultInt(job.getMaxAttempts(), DEFAULT_MAX_RETRY_COUNT)) {
      job.setJobStatus("PENDING");
      job.setNextRunAt(nextRunAt);
      job.setLockedBy(null);
      job.setLockedAt(null);
      job.setErrorMessage(message);
      publishJobRepository.saveAndFlush(job);
      return;
    }
    failJob(job, message, now);
  }

  private void failJob(PublishJobEntity job, String message, OffsetDateTime now) {
    job.setJobStatus("FAILED");
    job.setErrorMessage(message);
    job.setFailureDetails(appendFailure(job.getFailureDetails(), job.getJobType(), message));
    job.setCompletedAt(now);
    job.setLockedBy(null);
    job.setLockedAt(null);
    publishJobRepository.saveAndFlush(job);
  }

  private OffsetDateTime nextBackoffAt(
      PublishJobEntity job, StellnulaPublishRecordEntity record, OffsetDateTime now) {
    if (record.getNextRetryAt() != null && record.getNextRetryAt().isAfter(now)) {
      return record.getNextRetryAt();
    }
    int attempt = Math.max(defaultInt(job.getAttemptCount(), 1), 1);
    long backoffSeconds = Math.min(300L, 5L * (1L << Math.min(attempt - 1, 5)));
    return now.plusSeconds(backoffSeconds);
  }

  private void markRecordPublishedByQuery(
      StellnulaPublishRecordEntity record, StellnulaPublishResult result, OffsetDateTime now) {
    record.setPublishStatus("PUBLISHED");
    record.setTargetVersion(result.releaseNo());
    record.setPayloadMetadata(
        withStellnulaResult(record.getPayloadMetadata(), result, record.getDataId()));
    record.setChecksum(defaultText(result.checksum(), record.getChecksum()));
    record.setErrorMessage(null);
    record.setNextRetryAt(null);
    record.setPublishedAt(defaultTime(record.getPublishedAt(), now));
    stellnulaPublishRecordRepository.saveAndFlush(record);
  }

  private String publishLockKey(UUID instanceSpaceId, UUID applicationId) {
    return instanceSpaceId + ":" + applicationId;
  }

  private RuleReleaseEntity createRelease(
      PublishGovernanceRulesRequest request,
      String runtimeFormat,
      String idempotencyKey,
      int maxRetryCount) {
    RuleReleaseEntity release = new RuleReleaseEntity();
    release.setInstanceSpaceId(request.instanceSpaceId());
    release.setApplicationId(request.applicationId());
    release.setReleaseVersion(request.releaseVersion());
    release.setReleaseName(request.releaseName());
    release.setReleaseStatus("CREATED");
    release.setIdempotencyKey(idempotencyKey);
    release.setSourceFormat("CUE");
    release.setRuntimeFormat(runtimeFormat);
    release.setReleaseSnapshotJson(Map.of("state", "CREATED"));
    release.setChecksum(sha256(idempotencyKey));
    release.setReleaseNote(request.releaseNote());
    release.setRetryCount(0);
    release.setMaxRetryCount(maxRetryCount);
    release.setCreatedBy(request.publishedBy());
    release.setPublishedBy(request.publishedBy());
    return release;
  }

  private RuleReleaseEntity createRollbackRelease(
      RuleReleaseEntity sourceRelease,
      Long releaseVersion,
      String releaseName,
      String idempotencyKey,
      int maxRetryCount,
      RollbackRuleReleaseRequest request) {
    Map<String, Object> releaseSnapshot =
        buildRollbackReleaseSnapshot(sourceRelease, releaseVersion, releaseName, request);
    String releasePayload = toJson(releaseSnapshot);
    RuleReleaseEntity release = new RuleReleaseEntity();
    release.setInstanceSpaceId(sourceRelease.getInstanceSpaceId());
    release.setApplicationId(sourceRelease.getApplicationId());
    release.setReleaseVersion(releaseVersion);
    release.setReleaseName(releaseName);
    release.setReleaseStatus("CREATED");
    release.setIdempotencyKey(idempotencyKey);
    release.setSourceFormat("CUE");
    release.setRuntimeFormat("JSON");
    release.setReleaseSnapshotJson(releaseSnapshot);
    release.setReleaseSnapshotBytes(null);
    release.setChecksum(sha256(releasePayload));
    release.setRollbackFromReleaseId(sourceRelease.getId());
    release.setReleaseNote(defaultText(request.reason(), "rollback release"));
    release.setRetryCount(0);
    release.setMaxRetryCount(maxRetryCount);
    release.setCreatedBy(request.operator());
    release.setPublishedBy(request.operator());
    return release;
  }

  private Map<String, Object> buildRollbackReleaseSnapshot(
      RuleReleaseEntity sourceRelease,
      Long releaseVersion,
      String releaseName,
      RollbackRuleReleaseRequest request) {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("rollback", true);
    snapshot.put("rollbackFromReleaseId", sourceRelease.getId().toString());
    snapshot.put("rollbackFromReleaseVersion", sourceRelease.getReleaseVersion());
    snapshot.put("rollbackReason", request.reason());
    snapshot.put("instanceSpaceId", sourceRelease.getInstanceSpaceId().toString());
    snapshot.put("applicationId", sourceRelease.getApplicationId().toString());
    snapshot.put("releaseVersion", releaseVersion);
    snapshot.put("releaseName", releaseName);
    snapshot.put("sourceFormat", "CUE");
    snapshot.put("runtimeFormat", "JSON");
    snapshot.put("sourceReleaseSnapshot", sourceRelease.getReleaseSnapshotJson());
    return snapshot;
  }

  private List<ReleaseItemEntity> cloneReleaseItems(
      UUID rollbackReleaseId, List<ReleaseItemEntity> sourceItems) {
    List<ReleaseItemEntity> items = new ArrayList<>();
    for (ReleaseItemEntity sourceItem : sourceItems) {
      ReleaseItemEntity item = new ReleaseItemEntity();
      item.setReleaseId(rollbackReleaseId);
      item.setRuleId(sourceItem.getRuleId());
      item.setRuleType(sourceItem.getRuleType());
      item.setRuleCode(sourceItem.getRuleCode());
      item.setRuleName(sourceItem.getRuleName());
      item.setDraftVersion(sourceItem.getDraftVersion());
      item.setPriority(sourceItem.getPriority());
      item.setCueSource(sourceItem.getCueSource());
      item.setRuntimeSnapshotJson(copyMap(sourceItem.getRuntimeSnapshotJson()));
      item.setRuntimeSnapshotBytes(copyBytes(sourceItem.getRuntimeSnapshotBytes()));
      item.setChecksum(sourceItem.getChecksum());
      items.add(item);
    }
    return items;
  }

  private List<StellnulaPublishRecordEntity> clonePublishRecords(
      RuleReleaseEntity rollbackRelease,
      List<StellnulaPublishRecordEntity> sourceRecords,
      ApplicationEntity application,
      InstanceSpaceEntity instanceSpace,
      String configGroup,
      String idempotencyKey,
      String releaseName,
      String reason,
      String operator) {
    List<StellnulaPublishRecordEntity> records = new ArrayList<>();
    for (StellnulaPublishRecordEntity sourceRecord : sourceRecords) {
      StellnulaPublishRecordEntity record = new StellnulaPublishRecordEntity();
      record.setReleaseId(rollbackRelease.getId());
      record.setInstanceSpaceId(rollbackRelease.getInstanceSpaceId());
      record.setApplicationId(rollbackRelease.getApplicationId());
      record.setPublishKind(sourceRecord.getPublishKind());
      record.setNamespaceCode(sourceRecord.getNamespaceCode());
      record.setConfigGroup(defaultText(configGroup, sourceRecord.getConfigGroup()));
      record.setConfigKey(sourceRecord.getConfigKey());
      record.setDataId(sourceRecord.getDataId());
      record.setContentType(sourceRecord.getContentType());
      record.setRuntimeFormat(sourceRecord.getRuntimeFormat());
      record.setPayloadText(sourceRecord.getPayloadText());
      record.setPayloadBytes(copyBytes(sourceRecord.getPayloadBytes()));
      record.setPayloadMetadata(
          withRollbackMetadata(
              sourceRecord.getPayloadMetadata(),
              rollbackRelease,
              sourceRecord,
              application,
              instanceSpace,
              releaseName,
              reason,
              operator));
      record.setChecksum(sourceRecord.getChecksum());
      record.setIdempotencyKey(recordIdempotencyKey(idempotencyKey, sourceRecord.getDataId()));
      record.setRetryCount(0);
      record.setMaxRetryCount(
          defaultInt(rollbackRelease.getMaxRetryCount(), DEFAULT_MAX_RETRY_COUNT));
      record.setPublishStatus("PENDING");
      records.add(record);
    }
    return records;
  }

  private void transitionRelease(RuleReleaseEntity release, String status) {
    release.setReleaseStatus(status);
    ruleReleaseRepository.saveAndFlush(release);
  }

  private String normalizeRuntimeFormat(String runtimeFormat) {
    String normalized =
        runtimeFormat == null || runtimeFormat.isBlank()
            ? "JSON"
            : runtimeFormat.trim().toUpperCase();
    if (!RUNTIME_FORMATS.contains(normalized)) {
      throw new InvalidRuleRequestException("runtimeFormat必须是JSON或PROTOBUF");
    }
    return normalized;
  }

  private String normalizeConfigGroup(String configGroup) {
    return configGroup == null || configGroup.isBlank() ? DEFAULT_CONFIG_GROUP : configGroup.trim();
  }

  private List<GovernanceRuleEntity> findRules(PublishGovernanceRulesRequest request) {
    if (request.ruleIds() == null || request.ruleIds().isEmpty()) {
      return governanceRuleRepository
          .findByInstanceSpaceIdAndApplicationIdAndEnabledTrueOrderByPriorityAscRuleCodeAsc(
              request.instanceSpaceId(), request.applicationId());
    }
    return governanceRuleRepository
        .findByInstanceSpaceIdAndApplicationIdAndIdInAndEnabledTrueOrderByPriorityAscRuleCodeAsc(
            request.instanceSpaceId(), request.applicationId(), request.ruleIds());
  }

  private void validateRuleSelection(
      PublishGovernanceRulesRequest request, List<GovernanceRuleEntity> rules) {
    if (rules.isEmpty()) {
      throw new InvalidRuleRequestException("没有可发布的启用规则");
    }
    if (request.ruleIds() != null && !request.ruleIds().isEmpty()) {
      long requestedCount = request.ruleIds().stream().distinct().count();
      if (requestedCount != rules.size()) {
        throw new InvalidRuleRequestException("部分规则不存在、不属于当前作用域或未启用");
      }
    }
  }

  private List<CompiledGovernanceRule> compileRules(
      List<GovernanceRuleEntity> rules, ApplicationEntity application) {
    List<CompiledGovernanceRule> compiledRules = new ArrayList<>();
    for (GovernanceRuleEntity rule : rules) {
      compiledRules.add(governanceRuleContentCompiler.compile(rule, application));
    }
    return compiledRules;
  }

  private void validateSemanticChecks(
      PublishGovernanceRulesRequest request, List<CompiledGovernanceRule> compiledRules) {
    RuleSemanticCheckResult conflictResult = governanceRuleConflictDetector.detect(compiledRules);
    if (conflictResult.hasErrors()) {
      throw new InvalidRuleRequestException(String.join("; ", conflictResult.errors()));
    }
    RuleReleaseEntity previousRelease =
        ruleReleaseRepository
            .findTopByInstanceSpaceIdAndApplicationIdOrderByReleaseVersionDesc(
                request.instanceSpaceId(), request.applicationId())
            .orElse(null);
    RuleSemanticCheckResult compatibilityResult =
        governanceRuleCompatibilityValidator.validate(
            request.releaseVersion(), compiledRules, previousRelease);
    if (compatibilityResult.hasErrors()) {
      throw new InvalidRuleRequestException(String.join("; ", compatibilityResult.errors()));
    }
  }

  private List<Map<String, Object>> buildItemSnapshots(List<CompiledGovernanceRule> rules) {
    List<Map<String, Object>> snapshots = new ArrayList<>();
    for (CompiledGovernanceRule rule : rules) {
      snapshots.add(buildRuleSnapshot(rule));
    }
    return snapshots;
  }

  private Map<String, Object> buildReleaseSnapshot(
      PublishGovernanceRulesRequest request,
      String runtimeFormat,
      List<Map<String, Object>> itemSnapshots,
      ApplicationEntity application) {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("instanceSpaceId", request.instanceSpaceId().toString());
    snapshot.put("applicationId", request.applicationId().toString());
    snapshot.put("applicationCode", application.getApplicationCode());
    snapshot.put("releaseVersion", request.releaseVersion());
    snapshot.put("releaseName", request.releaseName());
    snapshot.put("sourceFormat", "CUE");
    snapshot.put("runtimeFormat", runtimeFormat);
    snapshot.put("rules", itemSnapshots);
    return snapshot;
  }

  private Map<String, Object> buildRuleSnapshot(CompiledGovernanceRule compiledRule) {
    GovernanceRuleEntity rule = compiledRule.rule();
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("ruleId", rule.getId().toString());
    snapshot.put("configId", compiledRule.configId());
    snapshot.put("ruleType", rule.getRuleType());
    snapshot.put("stellnulaRuleType", compiledRule.stellnulaRuleType());
    snapshot.put("ruleCode", rule.getRuleCode());
    snapshot.put("ruleName", rule.getRuleName());
    snapshot.put("targetService", compiledRule.targetService());
    snapshot.put("status", compiledRule.status());
    snapshot.put("priority", rule.getPriority());
    snapshot.put("draftVersion", rule.getDraftVersion());
    snapshot.put("schemaVersion", compiledRule.schemaVersion());
    snapshot.put("checksum", compiledRule.checksum());
    snapshot.put("content", compiledRule.contentModel());
    snapshot.put("warnings", compiledRule.warnings());
    snapshot.put("explain", compiledRule.explain());
    snapshot.put("cueSource", rule.getCueSource());
    return snapshot;
  }

  private void setReleasePayload(
      RuleReleaseEntity release,
      String runtimeFormat,
      Map<String, Object> releaseSnapshot,
      String releasePayload) {
    if ("JSON".equals(runtimeFormat)) {
      release.setReleaseSnapshotJson(releaseSnapshot);
      release.setReleaseSnapshotBytes(null);
      return;
    }
    release.setReleaseSnapshotJson(null);
    release.setReleaseSnapshotBytes(releasePayload.getBytes(StandardCharsets.UTF_8));
  }

  private List<ReleaseItemEntity> createReleaseItems(
      UUID releaseId, List<CompiledGovernanceRule> rules, String runtimeFormat) {
    List<ReleaseItemEntity> items = new ArrayList<>();
    for (CompiledGovernanceRule compiledRule : rules) {
      GovernanceRuleEntity rule = compiledRule.rule();
      Map<String, Object> snapshot = buildRuleSnapshot(compiledRule);
      String payload = toJson(snapshot);
      ReleaseItemEntity item = new ReleaseItemEntity();
      item.setReleaseId(releaseId);
      item.setRuleId(rule.getId());
      item.setRuleType(rule.getRuleType());
      item.setRuleCode(rule.getRuleCode());
      item.setRuleName(rule.getRuleName());
      item.setDraftVersion(rule.getDraftVersion());
      item.setPriority(rule.getPriority());
      item.setCueSource(rule.getCueSource());
      if ("JSON".equals(runtimeFormat)) {
        item.setRuntimeSnapshotJson(snapshot);
      } else {
        item.setRuntimeSnapshotBytes(compiledRule.protobufPayload());
      }
      item.setChecksum(compiledRule.checksum());
      items.add(item);
    }
    return items;
  }

  private List<StellnulaPublishRecordEntity> createPublishRecords(
      RuleReleaseEntity release,
      PublishGovernanceRulesRequest request,
      ApplicationEntity application,
      InstanceSpaceEntity instanceSpace,
      String configGroup,
      String idempotencyKey,
      List<CompiledGovernanceRule> compiledRules) {
    List<StellnulaPublishRecordEntity> records = new ArrayList<>();
    for (CompiledGovernanceRule compiledRule : compiledRules) {
      records.add(
          createPublishRecord(
              release,
              request,
              application,
              instanceSpace,
              configGroup,
              idempotencyKey,
              compiledRule));
      if ("AUTH".equals(compiledRule.rule().getRuleType())) {
        records.addAll(
            createAuthMaterialPublishRecords(
                release,
                request,
                application,
                instanceSpace,
                configGroup,
                idempotencyKey,
                compiledRule));
      }
    }
    return records;
  }

  private List<StellnulaPublishRecordEntity> createAuthMaterialPublishRecords(
      RuleReleaseEntity release,
      PublishGovernanceRulesRequest request,
      ApplicationEntity application,
      InstanceSpaceEntity instanceSpace,
      String configGroup,
      String idempotencyKey,
      CompiledGovernanceRule compiledRule) {
    List<AuthRuleCertificateEntity> bindings =
        authRuleCertificateRepository.findByAuthRuleId(compiledRule.rule().getId());
    List<StellnulaPublishRecordEntity> records = new ArrayList<>();
    for (AuthRuleCertificateEntity binding : bindings) {
      MtlsCertificateEntity certificate =
          mtlsCertificateRepository
              .findById(binding.getCertificateId())
              .orElseThrow(
                  () ->
                      new ResourceNotFoundException("MtlsCertificate", binding.getCertificateId()));
      validateCertificateForPublish(certificate, release);
      records.add(
          createCertificatePublishRecord(
              release,
              request,
              application,
              instanceSpace,
              configGroup,
              idempotencyKey,
              compiledRule,
              binding,
              certificate));
      if (isJwksBinding(binding, certificate)) {
        records.add(
            createJwksPublishRecord(
                release,
                request,
                application,
                instanceSpace,
                configGroup,
                idempotencyKey,
                compiledRule,
                binding,
                certificate));
      }
    }
    return records;
  }

  private StellnulaPublishRecordEntity createCertificatePublishRecord(
      RuleReleaseEntity release,
      PublishGovernanceRulesRequest request,
      ApplicationEntity application,
      InstanceSpaceEntity instanceSpace,
      String configGroup,
      String idempotencyKey,
      CompiledGovernanceRule compiledRule,
      AuthRuleCertificateEntity binding,
      MtlsCertificateEntity certificate) {
    Map<String, Object> payload =
        buildCertificatePayload(compiledRule, binding, certificate, application);
    String content = toJson(payload);
    String dataId =
        authMaterialConfigId(
            application, compiledRule.rule(), "mtls", binding.getBindingType(), certificate);
    return createSecurityPublishRecord(
        release,
        request,
        instanceSpace,
        configGroup,
        idempotencyKey,
        dataId,
        "MTLS_CERTIFICATE",
        certificate.getCertificateName(),
        content,
        sha256(content),
        true,
        securityMetadata(release, request, compiledRule, binding, certificate, instanceSpace));
  }

  private StellnulaPublishRecordEntity createJwksPublishRecord(
      RuleReleaseEntity release,
      PublishGovernanceRulesRequest request,
      ApplicationEntity application,
      InstanceSpaceEntity instanceSpace,
      String configGroup,
      String idempotencyKey,
      CompiledGovernanceRule compiledRule,
      AuthRuleCertificateEntity binding,
      MtlsCertificateEntity certificate) {
    Map<String, Object> payload = buildJwksPayload(compiledRule, binding, certificate, application);
    String content = toJson(payload);
    String dataId =
        authMaterialConfigId(
            application, compiledRule.rule(), "jwks", binding.getBindingType(), certificate);
    return createSecurityPublishRecord(
        release,
        request,
        instanceSpace,
        configGroup,
        idempotencyKey,
        dataId,
        "JWKS",
        certificate.getCertificateName(),
        content,
        sha256(content),
        false,
        securityMetadata(release, request, compiledRule, binding, certificate, instanceSpace));
  }

  private StellnulaPublishRecordEntity createSecurityPublishRecord(
      RuleReleaseEntity release,
      PublishGovernanceRulesRequest request,
      InstanceSpaceEntity instanceSpace,
      String configGroup,
      String idempotencyKey,
      String dataId,
      String publishKind,
      String ruleName,
      String content,
      String checksum,
      boolean sensitive,
      Map<String, Object> metadata) {
    StellnulaPublishRecordEntity record = new StellnulaPublishRecordEntity();
    record.setReleaseId(release.getId());
    record.setInstanceSpaceId(release.getInstanceSpaceId());
    record.setApplicationId(release.getApplicationId());
    record.setPublishKind(publishKind);
    record.setNamespaceCode(GOVERNANCE_NAMESPACE);
    record.setConfigGroup(configGroup);
    record.setConfigKey(dataId);
    record.setDataId(dataId);
    record.setContentType("application/json");
    record.setRuntimeFormat("JSON");
    record.setPayloadText(content);
    metadata.put("ruleName", ruleName);
    metadata.put("sensitive", sensitive);
    metadata.put("env", defaultText(request.env(), instanceSpace.getEnvironment()));
    metadata.put("region", defaultText(request.region(), DEFAULT_SCOPE));
    metadata.put("zone", defaultText(request.zone(), DEFAULT_SCOPE));
    metadata.put("cluster", defaultText(request.cluster(), DEFAULT_SCOPE));
    metadata.put("scopeMode", defaultText(request.scopeMode(), "INHERITABLE"));
    metadata.put("releaseName", request.releaseName());
    metadata.put(
        "releaseNote", defaultText(request.releaseNote(), "publish from stellorbit-service"));
    record.setPayloadMetadata(metadata);
    record.setChecksum(checksum);
    record.setIdempotencyKey(recordIdempotencyKey(idempotencyKey, dataId));
    record.setRetryCount(0);
    record.setMaxRetryCount(defaultInt(release.getMaxRetryCount(), DEFAULT_MAX_RETRY_COUNT));
    record.setPublishStatus("PENDING");
    return record;
  }

  private Map<String, Object> buildCertificatePayload(
      CompiledGovernanceRule compiledRule,
      AuthRuleCertificateEntity binding,
      MtlsCertificateEntity certificate,
      ApplicationEntity application) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("schemaVersion", "stellorbit.security.v1");
    payload.put("kind", "MTLS_CERTIFICATE");
    payload.put("applicationCode", application.getApplicationCode());
    payload.put("authRule", authRuleRef(compiledRule));
    payload.put("binding", bindingRef(binding));
    payload.put("certificate", certificateMaterial(certificate, true));
    return payload;
  }

  private Map<String, Object> buildJwksPayload(
      CompiledGovernanceRule compiledRule,
      AuthRuleCertificateEntity binding,
      MtlsCertificateEntity certificate,
      ApplicationEntity application) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("schemaVersion", "stellorbit.security.v1");
    payload.put("kind", "JWKS");
    payload.put("applicationCode", application.getApplicationCode());
    payload.put("authRule", authRuleRef(compiledRule));
    payload.put("binding", bindingRef(binding));
    payload.put("keys", List.of(jwksKey(certificate)));
    payload.put("certificate", certificateMaterial(certificate, false));
    return payload;
  }

  private Map<String, Object> certificateMaterial(
      MtlsCertificateEntity certificate, boolean includePrivateKey) {
    Map<String, Object> material = new LinkedHashMap<>();
    material.put("certificateId", certificate.getId().toString());
    material.put("certificateCode", certificate.getCertificateCode());
    material.put("certificateName", certificate.getCertificateName());
    material.put("certificateType", certificate.getCertificateType());
    material.put("usageType", certificate.getUsageType());
    material.put("trustDomain", certificate.getTrustDomain());
    material.put("subjectDn", certificate.getSubjectDn());
    material.put("issuerDn", certificate.getIssuerDn());
    material.put("serialNumber", certificate.getSerialNumber());
    material.put("fingerprintSha256", certificate.getFingerprintSha256());
    material.put("certificateChainPem", certificate.getCertificateChainPem());
    material.put("publicCertificatePem", certificate.getPublicCertificatePem());
    material.put("notBefore", certificate.getNotBefore().toString());
    material.put("notAfter", certificate.getNotAfter().toString());
    material.put("status", certificate.getStatus());
    if (includePrivateKey && certificate.getEncryptedPrivateKey() != null) {
      material.put(
          "encryptedPrivateKeyBase64",
          Base64.getEncoder().encodeToString(certificate.getEncryptedPrivateKey()));
      material.put("privateKeyAlgorithm", certificate.getPrivateKeyAlgorithm());
      material.put("encryptionKeyId", certificate.getEncryptionKeyId());
    }
    return material;
  }

  private Map<String, Object> jwksKey(MtlsCertificateEntity certificate) {
    Map<String, Object> key = new LinkedHashMap<>();
    key.put("kid", certificate.getFingerprintSha256());
    key.put("use", "sig");
    key.put("alg", certificate.getPrivateKeyAlgorithm());
    key.put("x5t#S256", certificate.getFingerprintSha256());
    key.put("x5c", certificateChain(certificate.getCertificateChainPem()));
    key.put("publicCertificatePem", certificate.getPublicCertificatePem());
    return key;
  }

  private List<String> certificateChain(String certificateChainPem) {
    if (certificateChainPem == null || certificateChainPem.isBlank()) {
      return List.of();
    }
    List<String> chain = new ArrayList<>();
    String[] blocks = certificateChainPem.split("-----END CERTIFICATE-----");
    for (String block : blocks) {
      String normalized =
          block
              .replace("-----BEGIN CERTIFICATE-----", "")
              .replace("\r", "")
              .replace("\n", "")
              .trim();
      if (!normalized.isBlank()) {
        chain.add(normalized);
      }
    }
    return chain;
  }

  private Map<String, Object> authRuleRef(CompiledGovernanceRule compiledRule) {
    GovernanceRuleEntity rule = compiledRule.rule();
    Map<String, Object> ref = new LinkedHashMap<>();
    ref.put("ruleId", rule.getId().toString());
    ref.put("ruleCode", rule.getRuleCode());
    ref.put("ruleName", rule.getRuleName());
    ref.put("configId", compiledRule.configId());
    ref.put("targetService", compiledRule.targetService());
    return ref;
  }

  private Map<String, Object> bindingRef(AuthRuleCertificateEntity binding) {
    Map<String, Object> ref = new LinkedHashMap<>();
    ref.put("bindingId", binding.getId().toString());
    ref.put("bindingType", binding.getBindingType());
    return ref;
  }

  private Map<String, Object> securityMetadata(
      RuleReleaseEntity release,
      PublishGovernanceRulesRequest request,
      CompiledGovernanceRule compiledRule,
      AuthRuleCertificateEntity binding,
      MtlsCertificateEntity certificate,
      InstanceSpaceEntity instanceSpace) {
    GovernanceRuleEntity rule = compiledRule.rule();
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("releaseVersion", release.getReleaseVersion());
    metadata.put("ruleId", rule.getId().toString());
    metadata.put("ruleType", rule.getRuleType());
    metadata.put("ruleName", rule.getRuleName());
    metadata.put("authConfigId", compiledRule.configId());
    metadata.put("bindingId", binding.getId().toString());
    metadata.put("bindingType", binding.getBindingType());
    metadata.put("certificateId", certificate.getId().toString());
    metadata.put("certificateCode", certificate.getCertificateCode());
    metadata.put("certificateType", certificate.getCertificateType());
    metadata.put("usageType", certificate.getUsageType());
    metadata.put("fingerprintSha256", certificate.getFingerprintSha256());
    metadata.put("env", defaultText(request.env(), instanceSpace.getEnvironment()));
    return metadata;
  }

  private String authMaterialConfigId(
      ApplicationEntity application,
      GovernanceRuleEntity rule,
      String materialType,
      String bindingType,
      MtlsCertificateEntity certificate) {
    return "stellorbit."
        + normalizeConfigSegment(application.getApplicationCode())
        + ".auth."
        + normalizeConfigSegment(rule.getRuleCode())
        + "."
        + normalizeConfigSegment(materialType)
        + "."
        + normalizeConfigSegment(bindingType)
        + "."
        + normalizeConfigSegment(certificate.getCertificateCode());
  }

  private boolean isJwksBinding(
      AuthRuleCertificateEntity binding, MtlsCertificateEntity certificate) {
    return "JWKS".equals(binding.getBindingType()) || "JWKS".equals(certificate.getUsageType());
  }

  private void validateCertificateForPublish(
      MtlsCertificateEntity certificate, RuleReleaseEntity release) {
    if (!release.getInstanceSpaceId().equals(certificate.getInstanceSpaceId())) {
      throw new InvalidRuleRequestException("证书不属于当前实例空间: " + certificate.getId());
    }
    if (certificate.getApplicationId() != null
        && !release.getApplicationId().equals(certificate.getApplicationId())) {
      throw new InvalidRuleRequestException("证书不属于当前应用: " + certificate.getId());
    }
    if (!"ACTIVE".equals(certificate.getStatus())) {
      throw new InvalidRuleRequestException("证书不是ACTIVE状态: " + certificate.getId());
    }
    OffsetDateTime now = OffsetDateTime.now();
    if (certificate.getNotBefore().isAfter(now) || !certificate.getNotAfter().isAfter(now)) {
      throw new InvalidRuleRequestException("证书不在有效期内: " + certificate.getId());
    }
  }

  private StellnulaPublishRecordEntity createPublishRecord(
      RuleReleaseEntity release,
      PublishGovernanceRulesRequest request,
      ApplicationEntity application,
      InstanceSpaceEntity instanceSpace,
      String configGroup,
      String idempotencyKey,
      CompiledGovernanceRule compiledRule) {
    GovernanceRuleEntity rule = compiledRule.rule();
    StellnulaPublishRecordEntity record = new StellnulaPublishRecordEntity();
    record.setReleaseId(release.getId());
    record.setInstanceSpaceId(release.getInstanceSpaceId());
    record.setApplicationId(release.getApplicationId());
    record.setPublishKind(toPublishKind(rule.getRuleType()));
    record.setNamespaceCode(GOVERNANCE_NAMESPACE);
    record.setConfigGroup(configGroup);
    record.setConfigKey(compiledRule.configId());
    record.setDataId(compiledRule.configId());
    record.setContentType("application/json");
    record.setRuntimeFormat("JSON");
    record.setPayloadText(compiledRule.content());
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("releaseVersion", release.getReleaseVersion());
    metadata.put("ruleId", rule.getId().toString());
    metadata.put("ruleType", rule.getRuleType());
    metadata.put("ruleName", rule.getRuleName());
    metadata.put("stellnulaRuleType", compiledRule.stellnulaRuleType());
    metadata.put("schemaVersion", compiledRule.schemaVersion());
    metadata.put("targetService", compiledRule.targetService());
    metadata.put("env", defaultText(request.env(), instanceSpace.getEnvironment()));
    metadata.put("region", defaultText(request.region(), DEFAULT_SCOPE));
    metadata.put("zone", defaultText(request.zone(), DEFAULT_SCOPE));
    metadata.put("cluster", defaultText(request.cluster(), DEFAULT_SCOPE));
    metadata.put("scopeMode", defaultText(request.scopeMode(), "INHERITABLE"));
    metadata.put("releaseName", request.releaseName());
    metadata.put(
        "releaseNote", defaultText(request.releaseNote(), "publish from stellorbit-service"));
    record.setPayloadMetadata(metadata);
    record.setChecksum(compiledRule.checksum());
    record.setIdempotencyKey(recordIdempotencyKey(idempotencyKey, compiledRule.configId()));
    record.setRetryCount(0);
    record.setMaxRetryCount(defaultInt(release.getMaxRetryCount(), DEFAULT_MAX_RETRY_COUNT));
    record.setPublishStatus("PENDING");
    return record;
  }

  private String toPublishKind(String ruleType) {
    return switch (ruleType) {
      case "ROUTE" -> "ROUTE_RULES";
      case "BREAKER" -> "BREAKER_RULES";
      case "RATE_LIMIT" -> "RATE_LIMIT_RULES";
      case "AUTH" -> "AUTH_RULES";
      default -> "RULE_SNAPSHOT";
    };
  }

  private Map<String, Object> withStellnulaResult(
      Map<String, Object> metadata, StellnulaPublishResult result, String configId) {
    Map<String, Object> merged = new LinkedHashMap<>();
    if (metadata != null) {
      merged.putAll(metadata);
    }
    merged.put("configId", configId);
    merged.put("stellnulaReleaseNo", result.releaseNo());
    merged.put("stellnulaVersion", result.version());
    merged.put("stellnulaRevision", result.revision());
    merged.put("stellnulaReleaseStatus", result.releaseStatus());
    merged.put("stellnulaChecksum", result.checksum());
    return merged;
  }

  private Map<String, Object> withManualRecovery(
      Map<String, Object> metadata, String operator, String note, OffsetDateTime recoveredAt) {
    Map<String, Object> merged = new LinkedHashMap<>();
    if (metadata != null) {
      merged.putAll(metadata);
    }
    merged.put("manualRecoveredBy", operator);
    merged.put("manualRecoveredAt", recoveredAt.toString());
    merged.put("manualRecoveryNote", note);
    return merged;
  }

  private Map<String, Object> withRollbackMetadata(
      Map<String, Object> metadata,
      RuleReleaseEntity rollbackRelease,
      StellnulaPublishRecordEntity sourceRecord,
      ApplicationEntity application,
      InstanceSpaceEntity instanceSpace,
      String releaseName,
      String reason,
      String operator) {
    Map<String, Object> merged = new LinkedHashMap<>();
    if (metadata != null) {
      merged.putAll(metadata);
    }
    merged.put("rollback", true);
    merged.put("rollbackReleaseId", rollbackRelease.getId().toString());
    merged.put("rollbackFromReleaseId", rollbackRelease.getRollbackFromReleaseId().toString());
    merged.put("rollbackSourceRecordId", sourceRecord.getId().toString());
    merged.put("releaseVersion", rollbackRelease.getReleaseVersion());
    merged.put("releaseName", releaseName);
    merged.put("releaseNote", reason);
    merged.put("rollbackReason", reason);
    merged.put("rollbackOperator", operator);
    merged.put("applicationCode", application.getApplicationCode());
    merged.put(
        "env",
        defaultText(Objects.toString(merged.get("env"), null), instanceSpace.getEnvironment()));
    merged.put("region", defaultText(Objects.toString(merged.get("region"), null), DEFAULT_SCOPE));
    merged.put("zone", defaultText(Objects.toString(merged.get("zone"), null), DEFAULT_SCOPE));
    merged.put(
        "cluster", defaultText(Objects.toString(merged.get("cluster"), null), DEFAULT_SCOPE));
    merged.put(
        "scopeMode", defaultText(Objects.toString(merged.get("scopeMode"), null), "INHERITABLE"));
    return merged;
  }

  private void recordRollbackAudit(
      RuleReleaseEntity sourceRelease,
      RuleReleaseEntity rollbackRelease,
      RollbackRuleReleaseRequest request,
      Long releaseVersion,
      String reason) {
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("rollbackFromReleaseId", sourceRelease.getId().toString());
    detail.put("rollbackFromReleaseVersion", sourceRelease.getReleaseVersion());
    detail.put("rollbackReleaseId", rollbackRelease.getId().toString());
    detail.put("rollbackReleaseVersion", releaseVersion);
    detail.put("rollbackReason", reason);
    detail.put("idempotencyKey", rollbackRelease.getIdempotencyKey());
    detail.put("maxRetryCount", rollbackRelease.getMaxRetryCount());
    detail.put("configGroup", request.configGroup());

    AuditEventEntity auditEvent = new AuditEventEntity();
    auditEvent.setEventType("RULE_RELEASE_ROLLBACK");
    auditEvent.setResourceType("RULE_RELEASE");
    auditEvent.setResourceId(rollbackRelease.getId());
    auditEvent.setTenantId(ControlPlaneSecurityContextHolder.tenantId());
    auditEvent.setInstanceSpaceId(rollbackRelease.getInstanceSpaceId());
    auditEvent.setApplicationId(rollbackRelease.getApplicationId());
    auditEvent.setOperator(request.operator());
    auditEvent.setEventDetail(detail);
    auditEventRepository.saveAndFlush(auditEvent);
  }

  private List<Object> collectFailureDetails(List<StellnulaPublishRecordEntity> records) {
    List<Object> failures = new ArrayList<>();
    for (StellnulaPublishRecordEntity record : records) {
      if ("FAILED".equals(record.getPublishStatus())) {
        Map<String, Object> failure = new LinkedHashMap<>();
        failure.put("recordId", record.getId());
        failure.put("dataId", record.getDataId());
        failure.put("retryCount", record.getRetryCount());
        failure.put("errorMessage", record.getErrorMessage());
        failure.put("lastAttemptAt", record.getLastAttemptAt());
        failures.add(failure);
      }
    }
    return failures;
  }

  private List<Object> appendFailure(
      List<Object> source, String phase, RuntimeException exception) {
    return appendFailure(source, phase, exception.getMessage());
  }

  private List<Object> appendFailure(List<Object> source, String phase, String message) {
    List<Object> failures = new ArrayList<>();
    if (source != null) {
      failures.addAll(source);
    }
    Map<String, Object> failure = new LinkedHashMap<>();
    failure.put("phase", phase);
    failure.put("message", message);
    failure.put("occurredAt", OffsetDateTime.now().toString());
    failures.add(failure);
    return failures;
  }

  private String ruleNameFrom(StellnulaPublishRecordEntity record) {
    Object ruleName = record.getPayloadMetadata().get("ruleName");
    return Objects.toString(ruleName, record.getDataId());
  }

  private String metadataText(
      StellnulaPublishRecordEntity record, String key, String defaultValue) {
    Object value =
        record.getPayloadMetadata() == null ? null : record.getPayloadMetadata().get(key);
    return defaultText(value == null ? null : value.toString(), defaultValue);
  }

  private String metadataText(PublishJobEntity job, String key, String defaultValue) {
    Object value = job.getPayloadMetadata() == null ? null : job.getPayloadMetadata().get(key);
    return defaultText(value == null ? null : value.toString(), defaultValue);
  }

  private boolean metadataBoolean(
      StellnulaPublishRecordEntity record, String key, boolean defaultValue) {
    Object value =
        record.getPayloadMetadata() == null ? null : record.getPayloadMetadata().get(key);
    if (value instanceof Boolean booleanValue) {
      return booleanValue;
    }
    if (value == null) {
      return defaultValue;
    }
    return Boolean.parseBoolean(value.toString());
  }

  private String idempotencyKey(PublishGovernanceRulesRequest request) {
    if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
      return request.idempotencyKey();
    }
    return "release-" + request.applicationId() + "-" + request.releaseVersion();
  }

  private String rollbackIdempotencyKey(
      UUID sourceReleaseId, Long releaseVersion, RollbackRuleReleaseRequest request) {
    if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
      return request.idempotencyKey();
    }
    return "rollback-" + sourceReleaseId + "-" + releaseVersion;
  }

  private String recordIdempotencyKey(String idempotencyKey, String dataId) {
    return "record-" + sha256(idempotencyKey + ":" + dataId);
  }

  private Long nextReleaseVersion(UUID instanceSpaceId, UUID applicationId) {
    return ruleReleaseRepository
        .findTopByInstanceSpaceIdAndApplicationIdOrderByReleaseVersionDesc(
            instanceSpaceId, applicationId)
        .map(release -> release.getReleaseVersion() + 1)
        .orElse(1L);
  }

  private int maxRetryCount(Integer maxRetryCount) {
    if (maxRetryCount == null) {
      return DEFAULT_MAX_RETRY_COUNT;
    }
    if (maxRetryCount < 0) {
      throw new InvalidRuleRequestException("最大重试次数不能小于0");
    }
    return maxRetryCount;
  }

  private int defaultInt(Integer value, int defaultValue) {
    return value == null ? defaultValue : value;
  }

  private OffsetDateTime defaultTime(OffsetDateTime value, OffsetDateTime defaultValue) {
    return value == null ? defaultValue : value;
  }

  private String defaultText(String value, String defaultValue) {
    return value == null || value.isBlank() ? defaultValue : value;
  }

  private String normalizeConfigSegment(String value) {
    return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "-");
  }

  private Map<String, Object> copyMap(Map<String, Object> source) {
    if (source == null) {
      return null;
    }
    return new LinkedHashMap<>(source);
  }

  private byte[] copyBytes(byte[] source) {
    if (source == null) {
      return null;
    }
    return source.clone();
  }

  private void markRulesPublished(UUID releaseId, OffsetDateTime publishedAt) {
    List<ReleaseItemEntity> items = releaseItemRepository.findByReleaseId(releaseId);
    List<GovernanceRuleEntity> rules = new ArrayList<>();
    for (ReleaseItemEntity item : items) {
      GovernanceRuleEntity rule =
          governanceRuleRepository
              .findById(item.getRuleId())
              .orElseThrow(() -> new ResourceNotFoundException("GovernanceRule", item.getRuleId()));
      rule.setStatus("PUBLISHED");
      rule.setLatestReleaseId(releaseId);
      rule.setPublishedAt(publishedAt);
      rule.setChecksum(item.getChecksum());
      rules.add(rule);
    }
    governanceRuleRepository.saveAll(rules);
  }

  private InstanceSpaceEntity findInstanceSpace(UUID id) {
    return instanceSpaceRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("InstanceSpace", id));
  }

  private ApplicationEntity findApplication(UUID id) {
    return applicationRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Application", id));
  }

  private RuleReleaseEntity findRelease(UUID id) {
    RuleReleaseEntity release =
        ruleReleaseRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("RuleRelease", id));
    ControlPlaneSecurityContextHolder.current()
        .ifPresent(
            context ->
                ControlPlaneSecurityContextHolder.requireInstanceSpace(
                    release.getInstanceSpaceId()));
    return release;
  }

  private RuleReleaseEntity findReleaseForWorker(UUID id) {
    return ruleReleaseRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("RuleRelease", id));
  }

  private void validateApplicationScope(UUID instanceSpaceId, ApplicationEntity application) {
    if (!instanceSpaceId.equals(application.getInstanceSpaceId())) {
      throw new InvalidRuleRequestException("应用不属于当前实例空间");
    }
  }

  private RuleReleaseResponse loadReleaseResponse(UUID releaseId) {
    RuleReleaseEntity release = findRelease(releaseId);
    List<ReleaseItemEntity> items = releaseItemRepository.findByReleaseId(releaseId);
    List<StellnulaPublishRecordEntity> publishRecords =
        stellnulaPublishRecordRepository.findByReleaseIdOrderByCreatedAtAsc(releaseId);
    return toResponse(release, items, publishRecords);
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new InvalidRuleRequestException("发布快照序列化失败: " + exception.getMessage());
    }
  }

  private String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hashed);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256算法不可用", exception);
    }
  }

  private RuleReleaseResponse toResponse(
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

  private record ReleaseExecutionPlan(
      UUID releaseId, boolean shouldExecute, String operator, String reason) {}
}
