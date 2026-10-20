package com.aivora.chat.service;

import com.aivora.wallet.service.WalletService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatSettlementService {

  private final JdbcTemplate db;
  private final WalletService wallet;

  public ChatSettlementService(JdbcTemplate db, WalletService wallet) {
    this.db = db;
    this.wallet = wallet;
  }

  @Transactional
  public void complete(long sessionId, String key, String answer) {
    db.update(
      "insert into chat_message(session_id,role,content,request_key) values (?,'assistant',?,?)",
      sessionId,
      answer,
      key
    );
    db.update(
      "update chat_request set status='SUCCEEDED' where request_key=? and status='STARTED'",
      key
    );
  }

  @Transactional
  public void fail(long userId, String key, long cost, String message) {
    if (
      db.update(
        "update chat_request set status='FAILED',error_message=? where request_key=? and status='STARTED'",
        message,
        key
      ) != 1
    ) return;
    wallet.change(userId, cost, "CHAT_REFUND", "refund:" + key);
  }
}
