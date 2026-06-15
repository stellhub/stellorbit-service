package io.github.stellorbit.api.security;

import io.github.stellorbit.infrastructure.persistence.entity.AuditEventEntity;
import io.github.stellorbit.infrastructure.persistence.repository.AuditEventRepository;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ControlPlaneAuditService {

  private final AuditEventRepository auditEventRepository;

  public ControlPlaneAuditService(AuditEventRepository auditEventRepository) {
    this.auditEventRepository = auditEventRepository;
  }

  /** 记录控制面操作审计事件。 */
  public void record(
      String eventType,
      String resourceType,
      UUID resourceId,
      UUID instanceSpaceId,
      UUID applicationId,
      String operator,
      String reason,
      Map<String, Object> detail) {
    AuditEventEntity event = new AuditEventEntity();
    event.setEventType(eventType);
    event.setResourceType(resourceType);
    event.setResourceId(resourceId);
    event.setTenantId(ControlPlaneSecurityContextHolder.tenantId());
    event.setInstanceSpaceId(instanceSpaceId);
    event.setApplicationId(applicationId);
    event.setOperator(operator);
    event.setEventDetail(withReason(detail, reason));
    ControlPlaneSecurityContextHolder.current()
        .ifPresent(
            context -> {
              event.setUserAgent(context.userAgent());
              event.setOperatorIp(toInetAddress(context.remoteAddress()));
            });
    auditEventRepository.save(event);
  }

  private Map<String, Object> withReason(Map<String, Object> detail, String reason) {
    Map<String, Object> merged = new LinkedHashMap<>();
    if (detail != null) {
      merged.putAll(detail);
    }
    merged.put("reason", reason);
    ControlPlaneSecurityContextHolder.current()
        .ifPresent(
            context -> {
              merged.put("tenantId", context.tenantId());
              merged.put("requestId", context.requestId());
              merged.put("roles", context.roles());
            });
    return merged;
  }

  private InetAddress toInetAddress(String remoteAddress) {
    if (remoteAddress == null || remoteAddress.isBlank()) {
      return null;
    }
    try {
      return InetAddress.getByName(remoteAddress);
    } catch (UnknownHostException exception) {
      return null;
    }
  }
}
