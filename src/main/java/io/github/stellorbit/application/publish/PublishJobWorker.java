package io.github.stellorbit.application.publish;

import io.github.stellorbit.application.usecase.PublishGovernanceRulesUseCase;
import io.github.stellorbit.infrastructure.persistence.entity.PublishJobEntity;
import io.github.stellorbit.infrastructure.persistence.entity.PublishLockEntity;
import io.github.stellorbit.infrastructure.persistence.entity.RuleReleaseEntity;
import io.github.stellorbit.infrastructure.persistence.repository.PublishJobRepository;
import io.github.stellorbit.infrastructure.persistence.repository.PublishLockRepository;
import io.github.stellorbit.infrastructure.persistence.repository.RuleReleaseRepository;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PublishJobWorker {

  private static final Set<String> RUNNABLE_STATUSES = Set.of("PENDING", "FAILED");

  private final PublishWorkerProperties properties;
  private final PublishJobRepository publishJobRepository;
  private final PublishLockRepository publishLockRepository;
  private final RuleReleaseRepository ruleReleaseRepository;
  private final PublishGovernanceRulesUseCase publishGovernanceRulesUseCase;
  private final String workerId;

  public PublishJobWorker(
      PublishWorkerProperties properties,
      PublishJobRepository publishJobRepository,
      PublishLockRepository publishLockRepository,
      RuleReleaseRepository ruleReleaseRepository,
      PublishGovernanceRulesUseCase publishGovernanceRulesUseCase) {
    this.properties = properties;
    this.publishJobRepository = publishJobRepository;
    this.publishLockRepository = publishLockRepository;
    this.ruleReleaseRepository = ruleReleaseRepository;
    this.publishGovernanceRulesUseCase = publishGovernanceRulesUseCase;
    this.workerId = resolveWorkerId(properties);
  }

  /** 周期性推进可运行的发布任务。 */
  @Scheduled(fixedDelayString = "${stellorbit.publish.worker.fixed-delay-millis:1000}")
  public void runDueJobs() {
    if (!properties.isEnabled()) {
      return;
    }
    OffsetDateTime now = OffsetDateTime.now();
    List<PublishJobEntity> jobs =
        publishJobRepository
            .findByJobStatusInAndNextRunAtLessThanEqualOrderByNextRunAtAscCreatedAtAsc(
                RUNNABLE_STATUSES, now, PageRequest.of(0, properties.getBatchSize()));
    for (PublishJobEntity job : jobs) {
      publishGovernanceRulesUseCase.executePublishJob(job.getId(), workerId);
    }
  }

  /** 恢复因节点异常而长时间停留在RUNNING的任务。 */
  @Scheduled(fixedDelayString = "${stellorbit.publish.worker.fixed-delay-millis:1000}")
  @Transactional
  public void recoverRunningJobs() {
    if (!properties.isEnabled()) {
      return;
    }
    OffsetDateTime timeoutAt =
        OffsetDateTime.now().minusNanos(properties.getRunningJobTimeoutMillis() * 1_000_000);
    List<PublishJobEntity> jobs =
        publishJobRepository.findByJobStatusAndLockedAtBefore("RUNNING", timeoutAt);
    for (PublishJobEntity job : jobs) {
      job.setJobStatus("PENDING");
      job.setNextRunAt(OffsetDateTime.now());
      job.setErrorMessage("发布任务运行超时，已重新入队");
      job.setLockedBy(null);
      job.setLockedAt(null);
    }
    publishJobRepository.saveAll(jobs);
  }

  /** 清理过期发布锁，避免异常退出后永久阻塞同应用发布。 */
  @Scheduled(fixedDelayString = "${stellorbit.publish.worker.fixed-delay-millis:1000}")
  @Transactional
  public void expirePublishLocks() {
    if (!properties.isEnabled()) {
      return;
    }
    OffsetDateTime now = OffsetDateTime.now();
    List<PublishLockEntity> locks =
        publishLockRepository.findByLockStatusAndExpiresAtBefore("ACTIVE", now);
    for (PublishLockEntity lock : locks) {
      lock.setLockStatus("EXPIRED");
      lock.setReleasedAt(now);
      lock.setReleaseReason("publish lock expired by worker");
    }
    publishLockRepository.saveAll(locks);
  }

  /** 扫描卡住的PUBLISHING发布并重新生成补偿任务。 */
  @Scheduled(fixedDelayString = "${stellorbit.publish.worker.fixed-delay-millis:1000}")
  public void recoverStuckReleases() {
    if (!properties.isEnabled()) {
      return;
    }
    OffsetDateTime timeoutAt =
        OffsetDateTime.now().minusNanos(properties.getStuckReleaseTimeoutMillis() * 1_000_000);
    List<RuleReleaseEntity> releases =
        ruleReleaseRepository.findByReleaseStatusAndUpdatedAtBefore("PUBLISHING", timeoutAt);
    for (RuleReleaseEntity release : releases) {
      publishGovernanceRulesUseCase.recoverStuckRelease(release.getId(), workerId);
    }
  }

  private String resolveWorkerId(PublishWorkerProperties properties) {
    if (properties.getWorkerId() != null && !properties.getWorkerId().isBlank()) {
      return properties.getWorkerId();
    }
    try {
      return "stellorbit-worker-" + InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException exception) {
      return "stellorbit-worker-local";
    }
  }
}
