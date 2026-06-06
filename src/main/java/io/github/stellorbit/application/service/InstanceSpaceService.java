package io.github.stellorbit.application.service;

import io.github.stellorbit.infrastructure.persistence.entity.InstanceSpaceEntity;
import io.github.stellorbit.infrastructure.persistence.repository.InstanceSpaceRepository;
import org.springframework.stereotype.Service;

@Service
public class InstanceSpaceService extends CrudService<InstanceSpaceEntity> {

  public InstanceSpaceService(InstanceSpaceRepository repository) {
    super(repository, "InstanceSpace");
  }
}
