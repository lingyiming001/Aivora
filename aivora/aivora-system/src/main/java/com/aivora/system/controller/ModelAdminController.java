package com.aivora.system.controller;

import com.aivora.common.CurrentUser;
import com.aivora.system.dto.*;
import com.aivora.system.service.ModelAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class ModelAdminController {

  private final ModelAdminService service;
  private final CurrentUser current;

  public ModelAdminController(ModelAdminService service, CurrentUser current) {
    this.service = service;
    this.current = current;
  }

  @GetMapping("/providers")
  public Object providers() {
    current.admin();
    return service.providers();
  }

  @GetMapping("/models")
  public Object models() {
    current.admin();
    return service.models();
  }

  @PutMapping("/providers/{id}")
  public void provider(@PathVariable long id, @Valid @RequestBody ModelAdminProviderUpdateDTO req) {
    current.admin();
    service.provider(
      id,
      req.baseUrl(),
      req.apiKey(),
      req.enabled(),
      req.clearKey(),
      req.name(),
      current.id()
    );
  }

  @PutMapping("/models/{id}")
  public void model(@PathVariable long id, @Valid @RequestBody ModelAdminModelUpdateDTO req) {
    current.admin();
    service.model(
      id,
      req.providerId(),
      req.modelName(),
      req.starPerRequest(),
      req.enabled(),
      req.isDefault(),
      req.modelType(),
      current.id()
    );
  }

  @PostMapping("/models/configure")
  public void add(@Valid @RequestBody ModelAdminModelUpdateDTO req) {
    current.admin();
    service.model(
      null,
      req.providerId(),
      req.modelName(),
      req.starPerRequest(),
      req.enabled(),
      req.isDefault(),
      req.modelType(),
      current.id()
    );
  }

  @PostMapping("/models/{id}/test")
  public Object test(@PathVariable long id) {
    current.admin();
    return service.probe(id);
  }
}
