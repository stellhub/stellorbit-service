package io.github.stellorbit.api;

import io.github.stellorbit.api.dto.BatchRuleEnabledRequest;
import io.github.stellorbit.api.dto.BatchRuleEnabledResponse;
import io.github.stellorbit.api.dto.PageResponse;
import io.github.stellorbit.api.dto.RuleSummaryResponse;
import io.github.stellorbit.api.dto.RuleValidationResponse;
import io.github.stellorbit.api.dto.ValidateGovernanceRuleRequest;
import io.github.stellorbit.api.security.ControlPlaneSecurityContextHolder;
import io.github.stellorbit.application.usecase.ControlPlaneQueryUseCase;
import io.github.stellorbit.application.usecase.ValidateGovernanceRuleUseCase;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stellorbit/governance-rules")
public class GovernanceRuleController {

  private final ValidateGovernanceRuleUseCase validateGovernanceRuleUseCase;
  private final ControlPlaneQueryUseCase controlPlaneQueryUseCase;

  public GovernanceRuleController(
      ValidateGovernanceRuleUseCase validateGovernanceRuleUseCase,
      ControlPlaneQueryUseCase controlPlaneQueryUseCase) {
    this.validateGovernanceRuleUseCase = validateGovernanceRuleUseCase;
    this.controlPlaneQueryUseCase = controlPlaneQueryUseCase;
  }

  /** 分页搜索规则。 */
  @GetMapping("/search")
  public PageResponse<RuleSummaryResponse> search(
      @RequestParam(required = false) UUID instanceSpaceId,
      @RequestParam(required = false) UUID applicationId,
      @RequestParam(required = false) String ruleType,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) Boolean enabled,
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return controlPlaneQueryUseCase.searchRules(
        instanceSpaceId, applicationId, ruleType, status, enabled, keyword, page, size);
  }

  /** 批量启用或停用规则。 */
  @PatchMapping("/batch-enabled")
  public BatchRuleEnabledResponse batchEnabled(
      @Valid @RequestBody BatchRuleEnabledRequest request) {
    return controlPlaneQueryUseCase.batchSetEnabled(
        request.ruleIds(), request.enabled(), ControlPlaneSecurityContextHolder.operator());
  }

  /** 执行发布前结构化校验。 */
  @PostMapping("/{id}/validate")
  public RuleValidationResponse validate(
      @PathVariable UUID id, @Valid @RequestBody ValidateGovernanceRuleRequest request) {
    return validateGovernanceRuleUseCase.validate(id, ControlPlaneSecurityContextHolder.operator());
  }

  /** 分页查询规则校验记录。 */
  @GetMapping("/{id}/validations")
  public PageResponse<RuleValidationResponse> validations(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return controlPlaneQueryUseCase.listValidations(id, page, size);
  }
}
