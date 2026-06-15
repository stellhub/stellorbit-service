package io.github.stellorbit.api;

import io.github.stellorbit.application.service.AuthRuleCertificateService;
import io.github.stellorbit.infrastructure.persistence.entity.AuthRuleCertificateEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stellorbit/security/auth-rule-certificates")
public class AuthRuleCertificateController extends CrudController<AuthRuleCertificateEntity> {

  public AuthRuleCertificateController(AuthRuleCertificateService service) {
    super(service);
  }
}
