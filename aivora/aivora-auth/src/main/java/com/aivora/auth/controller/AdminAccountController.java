package com.aivora.auth.controller;

import com.aivora.auth.dto.*;
import com.aivora.auth.service.AdminAccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/account")
public class AdminAccountController {

  private final AdminAccountService service;

  public AdminAccountController(AdminAccountService service) {
    this.service = service;
  }

  @GetMapping
  public Object profile() {
    return service.profile();
  }

  @PutMapping
  public void update(@Valid @RequestBody AdminProfileDTO req) {
    service.profile(req);
  }

  @PutMapping("/password")
  public void password(@Valid @RequestBody AdminPasswordDTO req) {
    service.changePassword(req);
  }

  @PostMapping("/phone/code")
  public Object send(@Valid @RequestBody AdminPhoneCodeDTO req, HttpServletRequest http) {
    return service.sendCode(req, http.getRemoteAddr());
  }

  @PutMapping("/phone")
  public void phone(@Valid @RequestBody AdminPhoneBindDTO req) {
    service.bindPhone(req);
  }
}
