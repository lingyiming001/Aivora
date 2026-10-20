package com.aivora.message.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TokenBudgetServiceTest {

  @Test
  void thresholdBoundaryIsExact() {
    assertEquals(20.0, TokenBudgetService.remainingPercent(1000, 800));
    assertTrue(TokenBudgetService.remainingPercent(1000, 799) > 20);
    assertTrue(TokenBudgetService.remainingPercent(1000, 801) < 20);
  }

  @Test
  void exhaustionAndUnconfiguredBudgetsAreBounded() {
    assertEquals(0, TokenBudgetService.remainingPercent(1000, 1200));
    assertEquals(0, TokenBudgetService.remainingPercent(0, 100));
    assertEquals(100, TokenBudgetService.remainingPercent(1000, 0));
  }
}
