package com.aivora.ai.controller;

import com.aivora.ai.service.ModelService;
import org.springframework.web.bind.annotation.*;

@RestController
public class ModelController {

  private final ModelService models;

  public ModelController(ModelService models) {
    this.models = models;
  }

  @GetMapping("/api/models")
  public Object models() {
    return models.listEnabled();
  }
}
