package io.github.stellorbit.domain;

import java.util.UUID;

public interface Identifiable {

  UUID getId();

  void setId(UUID id);
}
