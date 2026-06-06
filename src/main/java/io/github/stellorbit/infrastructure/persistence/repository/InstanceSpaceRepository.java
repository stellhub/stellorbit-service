package io.github.stellorbit.infrastructure.persistence.repository;

import io.github.stellorbit.infrastructure.persistence.entity.InstanceSpaceEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstanceSpaceRepository extends JpaRepository<InstanceSpaceEntity, UUID> {}
