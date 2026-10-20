package com.aivora.payment.controller;

import com.aivora.common.CurrentUser;
import com.aivora.payment.service.RechargeService;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@Profile("dev")
@RestController
@RequestMapping("/api/recharge")
public class SimulatedPaymentController {

  private final RechargeService recharge;
  private final CurrentUser current;

  public SimulatedPaymentController(RechargeService recharge, CurrentUser current) {
    this.recharge = recharge;
    this.current = current;
  }

  @PostMapping("/orders/{id}/simulate-pay")
  public Map<String, Long> pay(@PathVariable UUID id) {
    return Map.of("balance", recharge.settle(id, current.id(), "sim:" + id));
  }
}
