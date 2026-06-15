package io.github.stellorbit.api;

import io.github.stellorbit.application.service.MtlsCertificateService;
import io.github.stellorbit.infrastructure.persistence.entity.MtlsCertificateEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stellorbit/security/mtls-certificates")
public class MtlsCertificateController extends CrudController<MtlsCertificateEntity> {

  public MtlsCertificateController(MtlsCertificateService service) {
    super(service);
  }
}
