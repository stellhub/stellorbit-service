package io.github.stellorbit.infrastructure.stellnula;

import io.github.stellorbit.application.port.StellnulaPublishClient;
import io.github.stellorbit.application.port.StellnulaPublishCommand;
import io.github.stellorbit.application.port.StellnulaPublishResult;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class StellnulaHttpPublishClient implements StellnulaPublishClient {

  private final RestClient restClient;

  public StellnulaHttpPublishClient(StellnulaProperties properties, RestClient.Builder builder) {
    this.restClient = builder.baseUrl(properties.getBaseUrl()).build();
  }

  /** 调用stellnula-service治理规则发布接口。 */
  @Override
  public StellnulaPublishResult publish(StellnulaPublishCommand command) {
    try {
      if (usesGenericConfigEndpoint(command)) {
        return publishGenericConfig(command);
      }
      MutationResponse response =
          restClient
              .put()
              .uri("/api/v1/governance/rules/{ruleId}", command.ruleId())
              .body(GovernanceRuleRequest.from(command))
              .retrieve()
              .body(MutationResponse.class);
      if (response == null) {
        return new StellnulaPublishResult(
            false, null, null, null, null, null, "stellnula-service返回空响应");
      }
      return new StellnulaPublishResult(
          true,
          response.releaseNo(),
          response.version(),
          response.revision(),
          response.releaseStatus(),
          response.checksum(),
          null);
    } catch (RestClientResponseException exception) {
      return new StellnulaPublishResult(
          false,
          null,
          null,
          null,
          null,
          null,
          "stellnula-service发布失败: "
              + exception.getStatusCode()
              + " "
              + exception.getResponseBodyAsString());
    } catch (RuntimeException exception) {
      return new StellnulaPublishResult(
          false, null, null, null, null, null, exception.getMessage());
    }
  }

  /** 调用stellnula-service反查配置当前发布结果。 */
  @Override
  public StellnulaPublishResult query(StellnulaPublishCommand command) {
    try {
      MutationResponse response =
          restClient
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path(queryPath(command))
                          .queryParam("env", command.env())
                          .queryParam("region", command.region())
                          .queryParam("zone", command.zone())
                          .queryParam("cluster", command.cluster())
                          .build(command.ruleId()))
              .retrieve()
              .body(MutationResponse.class);
      if (response == null) {
        return new StellnulaPublishResult(
            false, null, null, null, null, null, "stellnula-service返回空响应");
      }
      return new StellnulaPublishResult(
          command.checksum() == null || command.checksum().equals(response.checksum()),
          response.releaseNo(),
          response.version(),
          response.revision(),
          response.releaseStatus(),
          response.checksum(),
          null);
    } catch (RestClientResponseException exception) {
      return new StellnulaPublishResult(
          false,
          null,
          null,
          null,
          null,
          null,
          "stellnula-service反查失败: "
              + exception.getStatusCode()
              + " "
              + exception.getResponseBodyAsString());
    } catch (RuntimeException exception) {
      return new StellnulaPublishResult(
          false, null, null, null, null, null, exception.getMessage());
    }
  }

  private StellnulaPublishResult publishGenericConfig(StellnulaPublishCommand command) {
    MutationResponse response =
        restClient
            .put()
            .uri("/api/v1/configs/{configId}", command.ruleId())
            .header("X-Operator", command.operator())
            .body(ConfigRequest.from(command))
            .retrieve()
            .body(MutationResponse.class);
    if (response == null) {
      return new StellnulaPublishResult(
          false, null, null, null, null, null, "stellnula-service返回空响应");
    }
    return new StellnulaPublishResult(
        true,
        response.releaseNo(),
        response.version(),
        response.revision(),
        response.releaseStatus(),
        response.checksum(),
        null);
  }

  private boolean usesGenericConfigEndpoint(StellnulaPublishCommand command) {
    return switch (command.publishKind()) {
      case "MTLS_CERTIFICATE", "JWKS" -> true;
      default -> false;
    };
  }

  private String queryPath(StellnulaPublishCommand command) {
    return usesGenericConfigEndpoint(command)
        ? "/api/v1/configs/{configId}"
        : "/api/v1/governance/rules/{ruleId}";
  }

  private record GovernanceRuleRequest(
      String ruleName,
      String ownerId,
      String ownerType,
      String env,
      String region,
      String zone,
      String cluster,
      String scopeMode,
      String contentType,
      boolean sensitive,
      String description,
      String content,
      String reason) {

    static GovernanceRuleRequest from(StellnulaPublishCommand command) {
      return new GovernanceRuleRequest(
          command.ruleName(),
          command.ownerId(),
          command.ownerType(),
          command.env(),
          command.region(),
          command.zone(),
          command.cluster(),
          command.scopeMode(),
          command.contentType(),
          command.sensitive(),
          command.description(),
          command.content(),
          command.reason());
    }
  }

  private record ConfigRequest(
      String configName,
      String ownerType,
      String ownerId,
      String namespace,
      String group,
      String format,
      String contentType,
      boolean sensitive,
      String description,
      String env,
      String region,
      String zone,
      String cluster,
      String scopeMode,
      String content,
      String reason) {

    static ConfigRequest from(StellnulaPublishCommand command) {
      return new ConfigRequest(
          command.ruleName(),
          command.ownerType(),
          command.ownerId(),
          command.namespaceCode(),
          command.configGroup(),
          "json",
          command.contentType(),
          command.sensitive(),
          command.description(),
          command.env(),
          command.region(),
          command.zone(),
          command.cluster(),
          command.scopeMode(),
          command.content(),
          command.reason());
    }
  }

  private record MutationResponse(
      String configId,
      long scopeId,
      String releaseNo,
      Long version,
      Long revision,
      String releaseStatus,
      String checksum) {}
}
