package io.github.stellorbit.interfaces.http;

import io.github.stellorbit.application.service.ApplicationService;
import io.github.stellorbit.infrastructure.persistence.entity.ApplicationEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stellorbit/applications")
public class ApplicationController extends CrudController<ApplicationEntity> {

  public ApplicationController(ApplicationService service) {
    super(service);
  }
}
