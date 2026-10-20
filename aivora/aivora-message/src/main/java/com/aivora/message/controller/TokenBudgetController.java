package com.aivora.message.controller;

import com.aivora.common.CurrentUser;
import com.aivora.message.dto.*;
import com.aivora.message.service.TokenBudgetService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/status/budget")
public class TokenBudgetController {

  private final TokenBudgetService service;
  private final CurrentUser current;

  public TokenBudgetController(TokenBudgetService service, CurrentUser current) {
    this.service = service;
    this.current = current;
  }

  @GetMapping
  public Object status() {
    current.admin();
    return service.status();
  }

  @PutMapping
  public void save(@Valid @RequestBody TokenBudgetSettingsDTO s) {
    current.admin();
    service.configure(s.month(), s.limit(), s.threshold(), s.baseline(), s.enabled(), current.id());
  }

  @GetMapping("/notices")
  public Object notices() {
    current.admin();
    return service.notices(current.id());
  }

  @PostMapping("/notices/{id}/read")
  public void read(@PathVariable long id) {
    current.admin();
    service.read(current.id(), id);
  }
}
