package io.github.stellorbit.application.runtime;

import io.github.stellorbit.interfaces.http.dto.RuntimeNodeDirectoryResponse;
import io.github.stellorbit.interfaces.http.dto.RuntimeNodeResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RuntimeNodeDirectory {

  private final RuntimeRateLimitProperties properties;

  public RuntimeNodeDirectory(RuntimeRateLimitProperties properties) {
    this.properties = properties;
  }

  /** 返回客户端固定节点hash所需的运行时节点目录。 */
  public RuntimeNodeDirectoryResponse currentDirectory() {
    List<RuntimeNodeResponse> nodes = nodes();
    return new RuntimeNodeDirectoryResponse(
        "RENDEZVOUS_HASH",
        "rateLimitRuleId + ':' + limitKeyHash",
        "LOCAL_MEMORY_EVENTUAL_CONSISTENCY",
        ringVersion(nodes),
        properties.getNodeId(),
        OffsetDateTime.now(),
        nodes);
  }

  private List<RuntimeNodeResponse> nodes() {
    List<String> configuredNodes = properties.getNodes();
    if (configuredNodes == null || configuredNodes.isEmpty()) {
      return currentNodeOnly();
    }
    List<RuntimeNodeResponse> nodes = new ArrayList<>();
    for (String node : configuredNodes) {
      if (node == null || node.isBlank()) {
        continue;
      }
      String[] parts = node.split("\\|", -1);
      String nodeId = parts.length > 0 && !parts[0].isBlank() ? parts[0] : node;
      String address = parts.length > 1 && !parts[1].isBlank() ? parts[1] : nodeId;
      Integer weight = parts.length > 2 && !parts[2].isBlank() ? parseWeight(parts[2]) : 100;
      nodes.add(
          new RuntimeNodeResponse(
              nodeId,
              address,
              weight,
              properties.getNodeId().equals(nodeId),
              "HEALTHY",
              OffsetDateTime.now()));
    }
    if (nodes.isEmpty()) {
      return currentNodeOnly();
    }
    return nodes;
  }

  private List<RuntimeNodeResponse> currentNodeOnly() {
    return List.of(
        new RuntimeNodeResponse(
            properties.getNodeId(),
            properties.getNodeAddress(),
            100,
            Boolean.TRUE,
            "HEALTHY",
            OffsetDateTime.now()));
  }

  private Integer parseWeight(String value) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException exception) {
      return 100;
    }
  }

  private String ringVersion(List<RuntimeNodeResponse> nodes) {
    String material =
        nodes.stream()
            .map(node -> node.nodeId() + "|" + node.address() + "|" + node.weight())
            .sorted()
            .reduce("", (left, right) -> left + ";" + right);
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashed = digest.digest(material.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hashed).substring(0, 16);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256算法不可用", exception);
    }
  }
}
