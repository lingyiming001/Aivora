package com.aivora.auth.controller;

import com.aivora.auth.dto.*;
import com.aivora.auth.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class PhoneAuthController {

  private final PhoneOtpService otp;

  public PhoneAuthController(PhoneOtpService otp) {
    this.otp = otp;
  }

  @GetMapping("/capabilities")
  public Object capabilities() {
    return Map.of("phoneLogin", otp.available(), "developmentSms", otp.available());
  }

  @PostMapping("/sms/send")
  public Object send(@Valid @RequestBody PhoneAuthSendDTO req, HttpServletRequest http) {
    return otp.send(req.phone(), false, http.getRemoteAddr());
  }

  @PostMapping("/admin/sms/send")
  public Object adminSend(@Valid @RequestBody PhoneAuthSendDTO req, HttpServletRequest http) {
    return otp.send(req.phone(), true, http.getRemoteAddr());
  }

  @PostMapping("/sms/login")
  public AuthService.Tokens login(@Valid @RequestBody PhoneAuthLoginDTO req) {
    return otp.login(req.phone(), req.code(), false);
  }

  @PostMapping("/admin/sms/login")
  public AuthService.Tokens adminLogin(@Valid @RequestBody PhoneAuthLoginDTO req) {
    return otp.login(req.phone(), req.code(), true);
  }
}
