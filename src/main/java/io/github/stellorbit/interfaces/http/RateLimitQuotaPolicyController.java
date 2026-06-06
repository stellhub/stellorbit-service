package io.github.stellorbit.interfaces.http;

import io.github.stellorbit.application.service.RateLimitQuotaPolicyService;
import io.github.stellorbit.infrastructure.persistence.entity.RateLimitQuotaPolicyEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stellorbit/rules/rate-limit-quota-policies")
public class RateLimitQuotaPolicyController extends CrudController<RateLimitQuotaPolicyEntity> {

  public RateLimitQuotaPolicyController(RateLimitQuotaPolicyService service) {
    super(service);
  }
}
