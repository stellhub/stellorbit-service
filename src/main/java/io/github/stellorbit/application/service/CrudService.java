package io.github.stellorbit.application.service;

import io.github.stellorbit.domain.Identifiable;
import io.github.stellorbit.interfaces.http.error.ResourceNotFoundException;
import io.github.stellorbit.interfaces.http.security.AccessDeniedException;
import io.github.stellorbit.interfaces.http.security.ControlPlaneSecurityContextHolder;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public abstract class CrudService<T extends Identifiable> {

  private final JpaRepository<T, UUID> repository;
  private final String resourceName;

  protected CrudService(JpaRepository<T, UUID> repository, String resourceName) {
    this.repository = repository;
    this.resourceName = resourceName;
  }

  /** 查询全部资源。 */
  @Transactional(readOnly = true)
  public List<T> findAll() {
    return repository.findAll().stream().filter(this::canAccess).toList();
  }

  /** 按ID查询资源。 */
  @Transactional(readOnly = true)
  public T findById(UUID id) {
    T entity =
        repository.findById(id).orElseThrow(() -> new ResourceNotFoundException(resourceName, id));
    requireAccess(entity);
    return entity;
  }

  /** 创建资源。 */
  @Transactional
  public T create(T entity) {
    fillTenant(entity);
    requireAccess(entity);
    fillOperator(entity, "setCreatedBy");
    fillOperator(entity, "setUpdatedBy");
    return repository.save(entity);
  }

  /** 更新资源。 */
  @Transactional
  public T update(UUID id, T entity) {
    if (!repository.existsById(id)) {
      throw new ResourceNotFoundException(resourceName, id);
    }
    entity.setId(id);
    fillTenant(entity);
    requireAccess(entity);
    fillOperator(entity, "setUpdatedBy");
    return repository.save(entity);
  }

  /** 删除资源。 */
  @Transactional
  public void delete(UUID id) {
    if (!repository.existsById(id)) {
      throw new ResourceNotFoundException(resourceName, id);
    }
    findById(id);
    repository.deleteById(id);
  }

  private boolean canAccess(T entity) {
    Optional<String> entityTenantId = readString(entity, "getTenantId");
    if (entityTenantId.isPresent()) {
      boolean canAccessTenant =
          ControlPlaneSecurityContextHolder.current()
              .map(context -> entityTenantId.get().equals(context.tenantId()))
              .orElse(true);
      if (!canAccessTenant) {
        return false;
      }
    }
    Optional<UUID> entityInstanceSpaceId = readInstanceSpaceId(entity);
    if (entityInstanceSpaceId.isEmpty()) {
      return true;
    }
    return ControlPlaneSecurityContextHolder.current()
        .map(context -> entityInstanceSpaceId.get().equals(context.instanceSpaceId()))
        .orElse(true);
  }

  private void requireAccess(T entity) {
    readString(entity, "getTenantId").ifPresent(ControlPlaneSecurityContextHolder::requireTenant);
    readInstanceSpaceId(entity).ifPresent(ControlPlaneSecurityContextHolder::requireInstanceSpace);
  }

  private Optional<UUID> readInstanceSpaceId(T entity) {
    try {
      Method getter = entity.getClass().getMethod("getInstanceSpaceId");
      Object value = getter.invoke(entity);
      return value instanceof UUID uuid ? Optional.of(uuid) : Optional.empty();
    } catch (NoSuchMethodException exception) {
      return Optional.empty();
    } catch (IllegalAccessException | InvocationTargetException exception) {
      throw new AccessDeniedException("数据权限校验失败");
    }
  }

  private Optional<String> readString(T entity, String getterName) {
    try {
      Method getter = entity.getClass().getMethod(getterName);
      Object value = getter.invoke(entity);
      return value instanceof String string && !string.isBlank()
          ? Optional.of(string)
          : Optional.empty();
    } catch (NoSuchMethodException exception) {
      return Optional.empty();
    } catch (IllegalAccessException | InvocationTargetException exception) {
      throw new AccessDeniedException("数据权限校验失败");
    }
  }

  private void fillTenant(T entity) {
    ControlPlaneSecurityContextHolder.current()
        .ifPresent(
            context -> {
              try {
                Method setter = entity.getClass().getMethod("setTenantId", String.class);
                setter.invoke(entity, context.tenantId());
              } catch (NoSuchMethodException ignored) {
                // Entity has no tenant field.
              } catch (IllegalAccessException | InvocationTargetException exception) {
                throw new AccessDeniedException("租户身份透传失败");
              }
            });
  }

  private void fillOperator(T entity, String setterName) {
    ControlPlaneSecurityContextHolder.current()
        .ifPresent(
            context -> {
              try {
                Method setter = entity.getClass().getMethod(setterName, String.class);
                setter.invoke(entity, context.operator());
              } catch (NoSuchMethodException ignored) {
                // Entity has no operator field.
              } catch (IllegalAccessException | InvocationTargetException exception) {
                throw new AccessDeniedException("操作人身份透传失败");
              }
            });
  }
}
