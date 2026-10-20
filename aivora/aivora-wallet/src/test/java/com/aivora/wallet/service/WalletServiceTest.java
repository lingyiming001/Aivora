package com.aivora.wallet.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivora.common.ApiException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class WalletServiceTest {

  @Test
  void repeatedReferenceDoesNotDebitAgain() {
    JdbcTemplate db = mock(JdbcTemplate.class);
    when(
      db.query(
        eq("select balance_after from wallet_ledger where reference_key=?"),
        any(org.springframework.jdbc.core.RowMapper.class),
        eq("chat:req-1")
      )
    ).thenReturn(List.of(8L));
    assertEquals(8L, new WalletService(db).change(7L, -2L, "CHAT", "chat:req-1"));
    verify(db, never()).update(startsWith("update wallet"), any(), any());
  }

  @Test
  void insufficientBalanceDoesNotWriteLedger() {
    JdbcTemplate db = mock(JdbcTemplate.class);
    when(
      db.query(
        eq("select balance_after from wallet_ledger where reference_key=?"),
        any(org.springframework.jdbc.core.RowMapper.class),
        eq("chat:req-2")
      )
    ).thenReturn(List.of());
    when(
      db.queryForObject(
        eq("select balance from wallet where user_id=? for update"),
        eq(Long.class),
        eq(7L)
      )
    ).thenReturn(1L);
    assertEquals(
      "INSUFFICIENT_STARS",
      assertThrows(ApiException.class, () ->
        new WalletService(db).change(7L, -2L, "CHAT", "chat:req-2")
      ).code()
    );
    verify(db, never()).update(
      startsWith("insert into wallet_ledger"),
      any(),
      any(),
      any(),
      any(),
      any()
    );
  }
}
