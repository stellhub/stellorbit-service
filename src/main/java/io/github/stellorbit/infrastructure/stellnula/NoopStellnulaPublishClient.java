package io.github.stellorbit.infrastructure.stellnula;

import io.github.stellorbit.application.port.StellnulaPublishClient;
import io.github.stellorbit.application.port.StellnulaPublishCommand;
import io.github.stellorbit.application.port.StellnulaPublishResult;

public class NoopStellnulaPublishClient implements StellnulaPublishClient {

  /** 模拟发布配置到stellnula-service。 */
  @Override
  public StellnulaPublishResult publish(StellnulaPublishCommand command) {
    String checksum = command.checksum() == null ? "unknown" : command.checksum();
    String releaseNo = "noop-" + checksum.substring(0, Math.min(16, checksum.length()));
    return new StellnulaPublishResult(true, releaseNo, 1L, 1L, "PUBLISHED", checksum, null);
  }

  /** 模拟反查配置中心发布结果。 */
  @Override
  public StellnulaPublishResult query(StellnulaPublishCommand command) {
    return publish(command);
  }
}
