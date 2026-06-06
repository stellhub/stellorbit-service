package io.github.stellorbit.application.port;

import java.util.List;

public record RuleSemanticCheckResult(List<String> errors, List<String> warnings) {

  public boolean hasErrors() {
    return errors != null && !errors.isEmpty();
  }
}
