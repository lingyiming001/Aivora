package com.aivora.payment.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivora.wallet.service.WalletService;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class RechargeServiceTest {

  @Test
  void paidOrderIsNotCreditedTwice() {
    JdbcTemplate db = mock(JdbcTemplate.class);
    WalletService wallet = mock(WalletService.class);
    UUID id = UUID.randomUUID();
    when(
      db.queryForList("select user_id,stars,status from recharge_order where id=? for update", id)
    ).thenReturn(List.of(Map.of("user_id", 7L, "stars", 100L, "status", "PAID")));
    when(wallet.balance(7L)).thenReturn(100L);
    assertEquals(100L, new RechargeService(db, wallet).settle(id, 7L, "sim:" + id));
    verify(wallet, never()).change(anyLong(), anyLong(), anyString(), anyString());
  }
}
