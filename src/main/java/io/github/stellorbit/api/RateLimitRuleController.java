package io.github.stellorbit.api;

import io.github.stellorbit.application.service.RateLimitRuleService;
import io.github.stellorbit.api.dto.CreateRateLimitRuleRequest;
import io.github.stellorbit.api.dto.RateLimitRuleDetailResponse;
import io.github.stellorbit.api.dto.RuleAggregateResponse;
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
@RequestMapping("/api/stellorbit/rules/rate-limits")
public class RateLimitRuleController {

  private final RateLimitRuleService service;

  public RateLimitRuleController(RateLimitRuleService service) {
    this.service = service;
  }

  /** 查询全部限流聚合规则。 */
  @GetMapping
  public List<RuleAggregateResponse<RateLimitRuleDetailResponse>> findAll() {
    return service.findAll();
  }

  /** 按ID查询限流聚合规则。 */
  @GetMapping("/{id}")
  public RuleAggregateResponse<RateLimitRuleDetailResponse> findById(@PathVariable UUID id) {
    return service.findById(id);
  }

  /** 创建限流聚合规则。 */
  @PostMapping
  public ResponseEntity<RuleAggregateResponse<RateLimitRuleDetailResponse>> create(
      @Valid @RequestBody CreateRateLimitRuleRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
  }

  /** 更新限流聚合规则。 */
  @PatchMapping("/{id}")
  public RuleAggregateResponse<RateLimitRuleDetailResponse> update(
      @PathVariable UUID id, @Valid @RequestBody CreateRateLimitRuleRequest request) {
    return service.update(id, request);
  }

  /** 删除限流聚合规则。 */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    service.delete(id);
  }
}
