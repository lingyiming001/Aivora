package com.aivora.payment.controller;

import com.aivora.common.CurrentUser;
import com.aivora.payment.dto.*;
import com.aivora.payment.service.RechargeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/recharge")
public class RechargeController {

  private final RechargeService recharge;
  private final CurrentUser current;
  private final Environment env;

  public RechargeController(RechargeService recharge, CurrentUser current, Environment env) {
    this.recharge = recharge;
    this.current = current;
    this.env = env;
  }

  @GetMapping("/packages")
  public Object packages() {
    return recharge.packages();
  }

  @GetMapping("/orders")
  public Object orders() {
    return recharge.orders(current.id());
  }

  @GetMapping("/capabilities")
  public Map<String, Boolean> capabilities() {
    return Map.of("simulatedPayment", env.acceptsProfiles(Profiles.of("dev")));
  }

  @PostMapping("/orders")
  public Map<String, UUID> place(@Valid @RequestBody RechargePlaceDTO req) {
    return Map.of("orderId", recharge.order(current.id(), req.packageId()));
  }
}
