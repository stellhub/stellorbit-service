package io.github.stellorbit.interfaces.http;

import io.github.stellorbit.application.service.AuthPolicyRuleService;
import io.github.stellorbit.interfaces.http.dto.AuthRuleDetailResponse;
import io.github.stellorbit.interfaces.http.dto.CreateAuthPolicyRuleRequest;
import io.github.stellorbit.interfaces.http.dto.RuleAggregateResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stellorbit/rules/auth")
public class AuthPolicyRuleController {

  private final AuthPolicyRuleService service;

  public AuthPolicyRuleController(AuthPolicyRuleService service) {
    this.service = service;
  }

  /** 查询全部鉴权聚合规则。 */
  @GetMapping
  public List<RuleAggregateResponse<AuthRuleDetailResponse>> findAll() {
    return service.findAll();
  }

  /** 按ID查询鉴权聚合规则。 */
  @GetMapping("/{id}")
  public RuleAggregateResponse<AuthRuleDetailResponse> findById(@PathVariable UUID id) {
    return service.findById(id);
  }

  /** 创建鉴权聚合规则。 */
  @PostMapping
  public ResponseEntity<RuleAggregateResponse<AuthRuleDetailResponse>> create(
      @Valid @RequestBody CreateAuthPolicyRuleRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
  }

  /** 更新鉴权聚合规则。 */
  @PatchMapping("/{id}")
  public RuleAggregateResponse<AuthRuleDetailResponse> update(
      @PathVariable UUID id, @Valid @RequestBody CreateAuthPolicyRuleRequest request) {
    return service.update(id, request);
  }

  /** 删除鉴权聚合规则。 */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    service.delete(id);
  }
}
