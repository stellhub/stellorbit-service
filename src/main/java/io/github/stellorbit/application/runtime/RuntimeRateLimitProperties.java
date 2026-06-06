package io.github.stellorbit.application.runtime;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "stellorbit.runtime")
public class RuntimeRateLimitProperties {

  private String nodeId = "stellorbit-local-1";
  private String nodeAddress = "http://127.0.0.1:8080";
  private List<String> nodes = new ArrayList<>();
  private long ruleSyncFixedDelayMillis = 1000L;
  private long counterCleanupFixedDelayMillis = 1000L;
  private long counterExpireAfterWindowMillis = 5000L;
  private long decisionFlushFixedDelayMillis = 1000L;
  private int decisionFlushBatchSize = 1000;
  private int decisionBufferCapacity = 100_000;
  private double decisionSampleRate = 0.01D;

  public String getNodeId() {
    return nodeId;
  }

  public void setNodeId(String nodeId) {
    this.nodeId = nodeId;
  }

  public String getNodeAddress() {
    return nodeAddress;
  }

  public void setNodeAddress(String nodeAddress) {
    this.nodeAddress = nodeAddress;
  }

  public List<String> getNodes() {
    return nodes;
  }

  public void setNodes(List<String> nodes) {
    this.nodes = nodes;
  }

  public long getRuleSyncFixedDelayMillis() {
    return ruleSyncFixedDelayMillis;
  }

  public void setRuleSyncFixedDelayMillis(long ruleSyncFixedDelayMillis) {
    this.ruleSyncFixedDelayMillis = ruleSyncFixedDelayMillis;
  }

  public long getCounterCleanupFixedDelayMillis() {
    return counterCleanupFixedDelayMillis;
  }

  public void setCounterCleanupFixedDelayMillis(long counterCleanupFixedDelayMillis) {
    this.counterCleanupFixedDelayMillis = counterCleanupFixedDelayMillis;
  }

  public long getCounterExpireAfterWindowMillis() {
    return counterExpireAfterWindowMillis;
  }

  public void setCounterExpireAfterWindowMillis(long counterExpireAfterWindowMillis) {
    this.counterExpireAfterWindowMillis = counterExpireAfterWindowMillis;
  }

  public long getDecisionFlushFixedDelayMillis() {
    return decisionFlushFixedDelayMillis;
  }

  public void setDecisionFlushFixedDelayMillis(long decisionFlushFixedDelayMillis) {
    this.decisionFlushFixedDelayMillis = decisionFlushFixedDelayMillis;
  }

  public int getDecisionFlushBatchSize() {
    return decisionFlushBatchSize;
  }

  public void setDecisionFlushBatchSize(int decisionFlushBatchSize) {
    this.decisionFlushBatchSize = decisionFlushBatchSize;
  }

  public int getDecisionBufferCapacity() {
    return decisionBufferCapacity;
  }

  public void setDecisionBufferCapacity(int decisionBufferCapacity) {
    this.decisionBufferCapacity = decisionBufferCapacity;
  }

  public double getDecisionSampleRate() {
    return decisionSampleRate;
  }

  public void setDecisionSampleRate(double decisionSampleRate) {
    this.decisionSampleRate = Math.max(0D, Math.min(decisionSampleRate, 1D));
  }
}
