package io.github.stellorbit.api;

import io.github.stellorbit.application.service.InstanceSpaceService;
import io.github.stellorbit.infrastructure.persistence.entity.InstanceSpaceEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stellorbit/instance-spaces")
public class InstanceSpaceController extends CrudController<InstanceSpaceEntity> {

  public InstanceSpaceController(InstanceSpaceService service) {
    super(service);
  }
}
