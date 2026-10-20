package com.aivora.wallet.service;

import com.aivora.common.ApiException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletService {

  private final JdbcTemplate db;

  public WalletService(JdbcTemplate db) {
    this.db = db;
  }

  public long balance(long userId) {
    return db.queryForObject("select balance from wallet where user_id=?", Long.class, userId);
  }

  @Transactional
  public long change(long userId, long delta, String reason, String key) {
    var existing = db.query(
      "select balance_after from wallet_ledger where reference_key=?",
      (rs, n) -> rs.getLong(1),
      key
    );
    if (!existing.isEmpty()) return existing.getFirst();
    long current = db.queryForObject(
      "select balance from wallet where user_id=? for update",
      Long.class,
      userId
    );
    if (delta < 0 && current < -delta) throw new ApiException(
      HttpStatus.PAYMENT_REQUIRED,
      "INSUFFICIENT_STARS",
      "星币余额不足"
    );
    long after = Math.addExact(current, delta);
    if (after < 0) throw new ApiException(
      HttpStatus.BAD_REQUEST,
      "NEGATIVE_BALANCE",
      "余额不能为负"
    );
    db.update("update wallet set balance=? where user_id=?", after, userId);
    db.update(
      "insert into wallet_ledger(user_id,delta,balance_after,reason,reference_key) values (?,?,?,?,?)",
      userId,
      delta,
      after,
      reason,
      key
    );
    return after;
  }

  public Object ledger(long userId) {
    return db.queryForList(
      "select l.id,l.delta,l.balance_after,l.reason,l.reference_key,l.created_at,c.name as character_name from wallet_ledger l left join chat_request r on r.request_key=substring(l.reference_key from position(':' in l.reference_key)+1) and r.user_id=l.user_id left join chat_session s on s.id=r.session_id and s.user_id=l.user_id left join character_card c on c.id=s.character_id where l.user_id=? order by l.id desc limit 100",
      userId
    );
  }
}
