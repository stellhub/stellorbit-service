package io.github.stellorbit.infrastructure.stellnula;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.github.stellorbit.application.port.StellnulaPublishCommand;
import io.github.stellorbit.application.port.StellnulaPublishResult;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class StellnulaHttpPublishClientTest {

  @Test
  void shouldPublishAuthRuleThroughGovernanceRuleEndpoint() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    StellnulaHttpPublishClient client = newClient(builder);

    server
        .expect(requestTo("http://stellnula.test/api/v1/governance/rules/stellorbit.demo.auth.r1"))
        .andExpect(method(HttpMethod.PUT))
        .andExpect(content().json("{\"ownerId\":\"demo-app\",\"env\":\"dev\",\"content\":\"{}\"}"))
        .andRespond(
            withSuccess(
                """
                {"configId":"stellorbit.demo.auth.r1","scopeId":1,"releaseNo":"REL-1","version":2,"revision":3,"releaseStatus":"PUBLISHED","checksum":"abc"}
                """,
                MediaType.APPLICATION_JSON));

    StellnulaPublishResult result = client.publish(command("AUTH_RULES"));

    assertThat(result.success()).isTrue();
    assertThat(result.releaseNo()).isEqualTo("REL-1");
    assertThat(result.version()).isEqualTo(2L);
    assertThat(result.revision()).isEqualTo(3L);
    assertThat(result.checksum()).isEqualTo("abc");
    server.verify();
  }

  @Test
  void shouldPublishSecurityMaterialThroughGenericConfigEndpointWithJsonFormat() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    StellnulaHttpPublishClient client = newClient(builder);

    server
        .expect(requestTo("http://stellnula.test/api/v1/configs/stellorbit.demo.auth.mtls.r1"))
        .andExpect(method(HttpMethod.PUT))
        .andExpect(
            content()
                .json(
                    """
                    {"ownerId":"demo-app","namespace":"governance-security","group":"security-materials","format":"json","contentType":"FILE","content":"{}"}
                    """))
        .andRespond(
            withSuccess(
                """
                {"configId":"stellorbit.demo.auth.mtls.r1","scopeId":1,"releaseNo":"REL-2","version":4,"revision":5,"releaseStatus":"PUBLISHED","checksum":"def"}
                """,
                MediaType.APPLICATION_JSON));

    StellnulaPublishResult result = client.publish(command("MTLS_CERTIFICATE"));

    assertThat(result.success()).isTrue();
    assertThat(result.releaseNo()).isEqualTo("REL-2");
    assertThat(result.checksum()).isEqualTo("def");
    server.verify();
  }

  @Test
  void shouldQueryGovernanceRuleWithScopeParameters() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    StellnulaHttpPublishClient client = newClient(builder);

    server
        .expect(
            requestTo(
                "http://stellnula.test/api/v1/governance/rules/stellorbit.demo.auth.r1?env=dev&region=default&zone=default&cluster=default"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                """
                {"ruleId":"stellorbit.demo.auth.r1","releaseNo":"REL-1","version":2,"revision":3,"releaseStatus":"PUBLISHED","checksum":"abc"}
                """,
                MediaType.APPLICATION_JSON));

    StellnulaPublishResult result = client.query(command("AUTH_RULES"));

    assertThat(result.success()).isTrue();
    assertThat(result.releaseNo()).isEqualTo("REL-1");
    assertThat(result.checksum()).isEqualTo("abc");
    server.verify();
  }

  private StellnulaHttpPublishClient newClient(RestClient.Builder builder) {
    StellnulaProperties properties = new StellnulaProperties();
    properties.setBaseUrl("http://stellnula.test");
    return new StellnulaHttpPublishClient(properties, builder);
  }

  private StellnulaPublishCommand command(String publishKind) {
    String ruleId =
        "MTLS_CERTIFICATE".equals(publishKind)
            ? "stellorbit.demo.auth.mtls.r1"
            : "stellorbit.demo.auth.r1";
    String namespace =
        "MTLS_CERTIFICATE".equals(publishKind) ? "governance-security" : "governance";
    String group =
        "MTLS_CERTIFICATE".equals(publishKind) ? "security-materials" : "service-governance";
    return new StellnulaPublishCommand(
        ruleId,
        "demo rule",
        "demo-app",
        "APPLICATION",
        namespace,
        group,
        publishKind,
        "dev",
        "default",
        "default",
        "default",
        "INHERITABLE",
        "FILE",
        false,
        "demo rule",
        "{}",
        "test publish",
        "tester",
        Map.of(),
        "abc");
  }
}
