package io.github.stellorbit.application.service;

import io.github.stellorbit.infrastructure.persistence.entity.ApplicationEntity;
import io.github.stellorbit.infrastructure.persistence.repository.ApplicationRepository;
import org.springframework.stereotype.Service;

@Service
public class ApplicationService extends CrudService<ApplicationEntity> {

  public ApplicationService(ApplicationRepository repository) {
    super(repository, "Application");
  }
}
