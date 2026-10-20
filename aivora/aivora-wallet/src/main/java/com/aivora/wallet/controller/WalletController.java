package com.aivora.wallet.controller;

import com.aivora.common.CurrentUser;
import com.aivora.wallet.service.WalletService;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {

  private final com.aivora.wallet.service.WalletUsageService usage;
  private final WalletService wallet;
  private final CurrentUser current;

  public WalletController(
    WalletService wallet,
    CurrentUser current,
    com.aivora.wallet.service.WalletUsageService usage
  ) {
    this.wallet = wallet;
    this.current = current;
    this.usage = usage;
  }

  @GetMapping
  public Map<String, Long> balance() {
    return Map.of("balance", wallet.balance(current.id()));
  }

  @GetMapping("/usage")
  public Object usage(@RequestParam(defaultValue = "30") int days) {
    return usage.summary(current.id(), days);
  }

  @GetMapping("/usage/details")
  public Object details(
    @RequestParam String day,
    @RequestParam long character,
    @RequestParam(defaultValue = "0") int page
  ) {
    return usage.details(current.id(), day, character, page);
  }

  @GetMapping("/ledger")
  public Object ledger() {
    return wallet.ledger(current.id());
  }
}
