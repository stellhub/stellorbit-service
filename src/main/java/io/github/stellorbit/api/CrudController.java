package io.github.stellorbit.api;

import io.github.stellorbit.application.service.CrudService;
import io.github.stellorbit.domain.Identifiable;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;

public abstract class CrudController<T extends Identifiable> {

  private final CrudService<T> service;

  protected CrudController(CrudService<T> service) {
    this.service = service;
  }

  /** 查询全部资源。 */
  @GetMapping
  public List<T> findAll() {
    return service.findAll();
  }

  /** 按ID查询资源。 */
  @GetMapping("/{id}")
  public T findById(@PathVariable UUID id) {
    return service.findById(id);
  }

  /** 创建资源。 */
  @PostMapping
  public ResponseEntity<T> create(@Valid @RequestBody T request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
  }

  /** 更新资源。 */
  @PatchMapping("/{id}")
  public T update(@PathVariable UUID id, @Valid @RequestBody T request) {
    return service.update(id, request);
  }

  /** 删除资源。 */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    service.delete(id);
  }
}
