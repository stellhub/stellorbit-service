package io.github.stellorbit.api.error;

import java.time.OffsetDateTime;

public record ApiErrorResponse(String message, int status, OffsetDateTime timestamp) {

  public static ApiErrorResponse of(String message, int status) {
    return new ApiErrorResponse(message, status, OffsetDateTime.now());
  }
}
