package io.github.stellorbit.api;

import io.github.stellorbit.api.dto.CreateRouteRuleRequest;
import io.github.stellorbit.api.dto.RouteRuleDetailResponse;
import io.github.stellorbit.api.dto.RuleAggregateResponse;
import io.github.stellorbit.application.service.RouteRuleService;
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
@RequestMapping("/api/stellorbit/rules/routes")
public class RouteRuleController {

  private final RouteRuleService service;

  public RouteRuleController(RouteRuleService service) {
    this.service = service;
  }

  /** 查询全部路由聚合规则。 */
  @GetMapping
  public List<RuleAggregateResponse<RouteRuleDetailResponse>> findAll() {
    return service.findAll();
  }

  /** 按ID查询路由聚合规则。 */
  @GetMapping("/{id}")
  public RuleAggregateResponse<RouteRuleDetailResponse> findById(@PathVariable UUID id) {
    return service.findById(id);
  }

  /** 创建路由聚合规则。 */
  @PostMapping
  public ResponseEntity<RuleAggregateResponse<RouteRuleDetailResponse>> create(
      @Valid @RequestBody CreateRouteRuleRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
  }

  /** 更新路由聚合规则。 */
  @PatchMapping("/{id}")
  public RuleAggregateResponse<RouteRuleDetailResponse> update(
      @PathVariable UUID id, @Valid @RequestBody CreateRouteRuleRequest request) {
    return service.update(id, request);
  }

  /** 删除路由聚合规则。 */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    service.delete(id);
  }
}
