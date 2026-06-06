package io.github.stellorbit.interfaces.http.error;

public class InvalidRuleRequestException extends RuntimeException {

  public InvalidRuleRequestException(String message) {
    super(message);
  }
}
