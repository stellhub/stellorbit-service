package io.github.stellorbit.application.publish;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "stellorbit.publish.worker")
public class PublishWorkerProperties {

  private boolean enabled = true;
  private String workerId;
  private long fixedDelayMillis = 1000;
  private int batchSize = 20;
  private long runningJobTimeoutMillis = 60000;
  private long stuckReleaseTimeoutMillis = 120000;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getWorkerId() {
    return workerId;
  }

  public void setWorkerId(String workerId) {
    this.workerId = workerId;
  }

  public long getFixedDelayMillis() {
    return fixedDelayMillis;
  }

  public void setFixedDelayMillis(long fixedDelayMillis) {
    this.fixedDelayMillis = fixedDelayMillis;
  }

  public int getBatchSize() {
    return batchSize;
  }

  public void setBatchSize(int batchSize) {
    this.batchSize = batchSize;
  }

  public long getRunningJobTimeoutMillis() {
    return runningJobTimeoutMillis;
  }

  public void setRunningJobTimeoutMillis(long runningJobTimeoutMillis) {
    this.runningJobTimeoutMillis = runningJobTimeoutMillis;
  }

  public long getStuckReleaseTimeoutMillis() {
    return stuckReleaseTimeoutMillis;
  }

  public void setStuckReleaseTimeoutMillis(long stuckReleaseTimeoutMillis) {
    this.stuckReleaseTimeoutMillis = stuckReleaseTimeoutMillis;
  }
}
