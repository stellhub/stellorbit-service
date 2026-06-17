package io.github.stellorbit.api;

import io.github.stellorbit.api.dto.BreakerRuleDetailResponse;
import io.github.stellorbit.api.dto.CreateBreakerRuleRequest;
import io.github.stellorbit.api.dto.RuleAggregateResponse;
import io.github.stellorbit.application.service.BreakerRuleService;
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
@RequestMapping("/api/stellorbit/rules/breakers")
public class BreakerRuleController {

  private final BreakerRuleService service;

  public BreakerRuleController(BreakerRuleService service) {
    this.service = service;
  }

  /** 查询全部熔断聚合规则。 */
  @GetMapping
  public List<RuleAggregateResponse<BreakerRuleDetailResponse>> findAll() {
    return service.findAll();
  }

  /** 按ID查询熔断聚合规则。 */
  @GetMapping("/{id}")
  public RuleAggregateResponse<BreakerRuleDetailResponse> findById(@PathVariable UUID id) {
    return service.findById(id);
  }

  /** 创建熔断聚合规则。 */
  @PostMapping
  public ResponseEntity<RuleAggregateResponse<BreakerRuleDetailResponse>> create(
      @Valid @RequestBody CreateBreakerRuleRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
  }

  /** 更新熔断聚合规则。 */
  @PatchMapping("/{id}")
  public RuleAggregateResponse<BreakerRuleDetailResponse> update(
      @PathVariable UUID id, @Valid @RequestBody CreateBreakerRuleRequest request) {
    return service.update(id, request);
  }

  /** 删除熔断聚合规则。 */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    service.delete(id);
  }
}
