package com.aivora.auth.controller;

import com.aivora.auth.dto.*;
import com.aivora.auth.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthService auth;

  public AuthController(AuthService auth) {
    this.auth = auth;
  }

  @PostMapping("/register")
  public AuthService.Tokens register(@Valid @RequestBody AuthRegisterDTO req) {
    return auth.register(req.email(), req.password(), req.nickname(), req.username());
  }

  @PostMapping("/login")
  public AuthService.Tokens login(@Valid @RequestBody AuthLoginDTO req) {
    return auth.login(req.identifier(), req.password());
  }

  @PostMapping("/admin/login")
  public AuthService.Tokens adminLogin(@Valid @RequestBody AuthLoginDTO req) {
    return auth.login(req.identifier(), req.password(), true);
  }

  @PostMapping("/refresh")
  public AuthService.Tokens refresh(@Valid @RequestBody AuthRefreshDTO req) {
    return auth.refresh(req.refreshToken());
  }

  @PostMapping("/logout")
  public void logout(@Valid @RequestBody AuthRefreshDTO req) {
    auth.logout(req.refreshToken());
  }
}
