package io.github.stellorbit.interfaces.http.error;

import java.util.UUID;

public class ResourceNotFoundException extends RuntimeException {

  public ResourceNotFoundException(String resourceName, UUID id) {
    super(resourceName + " not found: " + id);
  }
}
