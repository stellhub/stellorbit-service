package io.github.stellorbit.api;

import io.github.stellorbit.api.dto.ApprovalActionRequest;
import io.github.stellorbit.api.dto.ApprovalResponse;
import io.github.stellorbit.api.dto.PageResponse;
import io.github.stellorbit.api.dto.PublishGovernanceRulesRequest;
import io.github.stellorbit.api.dto.RecoverRuleReleaseRequest;
import io.github.stellorbit.api.dto.RetryRuleReleaseRequest;
import io.github.stellorbit.api.dto.RollbackRuleReleaseRequest;
import io.github.stellorbit.api.dto.RuleReleaseDiffResponse;
import io.github.stellorbit.api.dto.RuleReleaseDryRunResponse;
import io.github.stellorbit.api.dto.RuleReleaseImpactResponse;
import io.github.stellorbit.api.dto.RuleReleaseResponse;
import io.github.stellorbit.api.dto.RuleReleaseSummaryResponse;
import io.github.stellorbit.api.security.ControlPlaneSecurityContextHolder;
import io.github.stellorbit.application.usecase.ControlPlaneQueryUseCase;
import io.github.stellorbit.application.usecase.DryRunGovernanceRulesUseCase;
import io.github.stellorbit.application.usecase.PublishGovernanceRulesUseCase;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stellorbit/rule-releases")
public class RuleReleaseController {

  private final PublishGovernanceRulesUseCase publishGovernanceRulesUseCase;
  private final DryRunGovernanceRulesUseCase dryRunGovernanceRulesUseCase;
  private final ControlPlaneQueryUseCase controlPlaneQueryUseCase;

  public RuleReleaseController(
      PublishGovernanceRulesUseCase publishGovernanceRulesUseCase,
      DryRunGovernanceRulesUseCase dryRunGovernanceRulesUseCase,
      ControlPlaneQueryUseCase controlPlaneQueryUseCase) {
    this.publishGovernanceRulesUseCase = publishGovernanceRulesUseCase;
    this.dryRunGovernanceRulesUseCase = dryRunGovernanceRulesUseCase;
    this.controlPlaneQueryUseCase = controlPlaneQueryUseCase;
  }

  /** 分页查询发布列表。 */
  @GetMapping
  public PageResponse<RuleReleaseSummaryResponse> search(
      @RequestParam(required = false) UUID instanceSpaceId,
      @RequestParam(required = false) UUID applicationId,
      @RequestParam(required = false) String releaseStatus,
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return controlPlaneQueryUseCase.searchReleases(
        instanceSpaceId, applicationId, releaseStatus, keyword, page, size);
  }

  /** 查询发布详情。 */
  @GetMapping("/{id}")
  public RuleReleaseResponse detail(@PathVariable UUID id) {
    return controlPlaneQueryUseCase.getRelease(id);
  }

  /** 对比两个发布版本。 */
  @GetMapping("/{id}/diff")
  public RuleReleaseDiffResponse diff(@PathVariable UUID id, @RequestParam UUID baseReleaseId) {
    return controlPlaneQueryUseCase.diffReleases(baseReleaseId, id);
  }

  /** 分析发布影响面。 */
  @GetMapping("/{id}/impact")
  public RuleReleaseImpactResponse impact(@PathVariable UUID id) {
    return controlPlaneQueryUseCase.analyzeImpact(id);
  }

  /** 发布服务治理规则。 */
  @PostMapping
  public ResponseEntity<RuleReleaseResponse> publish(
      @Valid @RequestBody PublishGovernanceRulesRequest request) {
    PublishGovernanceRulesRequest securedRequest =
        new PublishGovernanceRulesRequest(
            request.instanceSpaceId(),
            request.applicationId(),
            request.releaseVersion(),
            request.releaseName(),
            request.runtimeFormat(),
            request.idempotencyKey(),
            request.maxRetryCount(),
            request.ruleIds(),
            ControlPlaneSecurityContextHolder.reason(),
            request.env(),
            request.region(),
            request.zone(),
            request.cluster(),
            request.scopeMode(),
            request.configGroup(),
            ControlPlaneSecurityContextHolder.operator());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(publishGovernanceRulesUseCase.publish(securedRequest));
  }

  /** 发布前dry-run编译和解释。 */
  @PostMapping("/dry-run")
  public RuleReleaseDryRunResponse dryRun(
      @Valid @RequestBody PublishGovernanceRulesRequest request) {
    return dryRunGovernanceRulesUseCase.dryRun(securedPublishRequest(request));
  }

  /** 重试发布失败或待处理的发布项。 */
  @PostMapping("/{id}/retry")
  public RuleReleaseResponse retry(
      @PathVariable UUID id, @Valid @RequestBody RetryRuleReleaseRequest request) {
    return publishGovernanceRulesUseCase.retry(id, securedRetryRequest(request));
  }

  /** 重试单条发布记录。 */
  @PostMapping("/{id}/publish-records/{recordId}/retry")
  public RuleReleaseResponse retryPublishRecord(
      @PathVariable UUID id,
      @PathVariable UUID recordId,
      @Valid @RequestBody RetryRuleReleaseRequest request) {
    return publishGovernanceRulesUseCase.retryPublishRecord(
        id, recordId, securedRetryRequest(request));
  }

  /** 人工恢复发布状态。 */
  @PostMapping("/{id}/recover")
  public RuleReleaseResponse recover(
      @PathVariable UUID id, @Valid @RequestBody RecoverRuleReleaseRequest request) {
    return publishGovernanceRulesUseCase.recover(
        id,
        new RecoverRuleReleaseRequest(
            ControlPlaneSecurityContextHolder.operator(),
            ControlPlaneSecurityContextHolder.reason(),
            request.markFailedRecordsAsPublished()));
  }

  /** 按历史发布版本回滚并重新发布到配置中心。 */
  @PostMapping("/{id}/rollback")
  public ResponseEntity<RuleReleaseResponse> rollback(
      @PathVariable UUID id, @Valid @RequestBody RollbackRuleReleaseRequest request) {
    RollbackRuleReleaseRequest securedRequest =
        new RollbackRuleReleaseRequest(
            request.releaseVersion(),
            request.releaseName(),
            request.idempotencyKey(),
            request.maxRetryCount(),
            ControlPlaneSecurityContextHolder.reason(),
            request.configGroup(),
            ControlPlaneSecurityContextHolder.operator());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(publishGovernanceRulesUseCase.rollback(id, securedRequest));
  }

  /** 查询发布审批时间线。 */
  @GetMapping("/{id}/approvals")
  public List<ApprovalResponse> approvals(@PathVariable UUID id) {
    return controlPlaneQueryUseCase.listApprovals(id);
  }

  /** 提交发布审批。 */
  @PostMapping("/{id}/approvals/submit")
  public ApprovalResponse submitApproval(
      @PathVariable UUID id, @Valid @RequestBody ApprovalActionRequest request) {
    return controlPlaneQueryUseCase.submitApproval(id, securedApprovalRequest());
  }

  /** 通过发布审批。 */
  @PostMapping("/{id}/approvals/approve")
  public ApprovalResponse approve(
      @PathVariable UUID id, @Valid @RequestBody ApprovalActionRequest request) {
    return controlPlaneQueryUseCase.approve(id, securedApprovalRequest());
  }

  /** 驳回发布审批。 */
  @PostMapping("/{id}/approvals/reject")
  public ApprovalResponse reject(
      @PathVariable UUID id, @Valid @RequestBody ApprovalActionRequest request) {
    return controlPlaneQueryUseCase.reject(id, securedApprovalRequest());
  }

  private RetryRuleReleaseRequest securedRetryRequest(RetryRuleReleaseRequest request) {
    return new RetryRuleReleaseRequest(
        ControlPlaneSecurityContextHolder.operator(),
        ControlPlaneSecurityContextHolder.reason(),
        request.maxRetryCount());
  }

  private PublishGovernanceRulesRequest securedPublishRequest(
      PublishGovernanceRulesRequest request) {
    return new PublishGovernanceRulesRequest(
        request.instanceSpaceId(),
        request.applicationId(),
        request.releaseVersion(),
        request.releaseName(),
        request.runtimeFormat(),
        request.idempotencyKey(),
        request.maxRetryCount(),
        request.ruleIds(),
        ControlPlaneSecurityContextHolder.reason(),
        request.env(),
        request.region(),
        request.zone(),
        request.cluster(),
        request.scopeMode(),
        request.configGroup(),
        ControlPlaneSecurityContextHolder.operator());
  }

  private ApprovalActionRequest securedApprovalRequest() {
    return new ApprovalActionRequest(
        ControlPlaneSecurityContextHolder.operator(), ControlPlaneSecurityContextHolder.reason());
  }
}
