package com.aivora.wallet.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aivora.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class WalletUsageServiceTest {

  @Test
  void rejectsUnboundedPeriodsBeforeQuerying() {
    var db = mock(JdbcTemplate.class);
    var s = new WalletUsageService(db);
    for (int period : new int[] { 0, 1, 365, -1 })
      assertEquals(
        "INVALID_PERIOD",
        assertThrows(ApiException.class, () -> s.summary(7, period)).code()
      );
    verifyNoInteractions(db);
  }

  @Test
  void rejectsInvalidDateAndPagination() {
    var db = mock(JdbcTemplate.class);
    var s = new WalletUsageService(db);
    assertThrows(ApiException.class, () -> s.details(7, "2026-02-30", 1, 0));
    assertThrows(ApiException.class, () -> s.details(7, "2026-10-14", -1, 0));
    assertThrows(ApiException.class, () -> s.details(7, "2026-10-14", 1, -1));
    verifyNoInteractions(db);
  }
}
