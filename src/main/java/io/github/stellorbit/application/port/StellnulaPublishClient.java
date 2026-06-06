package io.github.stellorbit.application.port;

public interface StellnulaPublishClient {

  /** 发布配置到stellnula-service。 */
  StellnulaPublishResult publish(StellnulaPublishCommand command);

  /** 反查stellnula-service中的配置发布结果。 */
  default StellnulaPublishResult query(StellnulaPublishCommand command) {
    return new StellnulaPublishResult(false, null, null, null, null, null, "当前客户端不支持发布结果反查");
  }
}
