package io.github.stellorbit.interfaces.http.error;

import io.github.stellorbit.interfaces.http.security.AccessDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ApiErrorResponse> handleResourceNotFound(
      ResourceNotFoundException exception) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ApiErrorResponse.of(exception.getMessage(), HttpStatus.NOT_FOUND.value()));
  }

  @ExceptionHandler(InvalidRuleRequestException.class)
  public ResponseEntity<ApiErrorResponse> handleInvalidRuleRequest(
      InvalidRuleRequestException exception) {
    return ResponseEntity.badRequest()
        .body(ApiErrorResponse.of(exception.getMessage(), HttpStatus.BAD_REQUEST.value()));
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException exception) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(ApiErrorResponse.of(exception.getMessage(), HttpStatus.FORBIDDEN.value()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiErrorResponse> handleValidation(
      MethodArgumentNotValidException exception) {
    String message =
        exception.getBindingResult().getAllErrors().stream()
            .findFirst()
            .map(
                error ->
                    error.getDefaultMessage() == null
                        ? "Invalid request"
                        : error.getDefaultMessage())
            .orElse("Invalid request");
    return ResponseEntity.badRequest()
        .body(ApiErrorResponse.of(message, HttpStatus.BAD_REQUEST.value()));
  }
}
