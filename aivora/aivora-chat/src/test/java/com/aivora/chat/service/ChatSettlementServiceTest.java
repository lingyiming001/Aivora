package com.aivora.chat.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivora.wallet.service.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ChatSettlementServiceTest {

  @Test
  void settledRequestCannotBeRefundedAgain() {
    var db = mock(JdbcTemplate.class);
    var wallet = mock(WalletService.class);
    new ChatSettlementService(db, wallet).fail(1, "key", 3, "error");
    verifyNoInteractions(wallet);
  }

  @Test
  void failedPendingRequestRefundsExactlyOnce() {
    var db = mock(JdbcTemplate.class);
    var wallet = mock(WalletService.class);
    when(db.update(anyString(), eq("error"), eq("key"))).thenReturn(1, 0);
    var s = new ChatSettlementService(db, wallet);
    s.fail(1, "key", 3, "error");
    s.fail(1, "key", 3, "error");
    verify(wallet, times(1)).change(1, 3, "CHAT_REFUND", "refund:key");
  }
}
