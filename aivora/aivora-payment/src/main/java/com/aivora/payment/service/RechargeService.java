package com.aivora.payment.service;

import com.aivora.common.ApiException;
import com.aivora.wallet.service.WalletService;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RechargeService {

  private final JdbcTemplate db;
  private final WalletService wallet;

  public RechargeService(JdbcTemplate db, WalletService wallet) {
    this.db = db;
    this.wallet = wallet;
  }

  public Object packages() {
    return db.queryForList(
      "select id,name,amount_cents,stars from recharge_package where enabled order by amount_cents"
    );
  }

  @Transactional
  public UUID order(long userId, long packageId) {
    var packages = db.queryForList(
      "select amount_cents,stars from recharge_package where id=? and enabled",
      packageId
    );
    if (packages.isEmpty()) throw new ApiException(
      HttpStatus.NOT_FOUND,
      "PACKAGE_NOT_FOUND",
      "充值套餐不存在"
    );
    var p = packages.getFirst();
    UUID id = UUID.randomUUID();
    db.update(
      "insert into recharge_order(id,user_id,package_id,amount_cents,stars) values (?,?,?,?,?)",
      id,
      userId,
      packageId,
      p.get("amount_cents"),
      p.get("stars")
    );
    return id;
  }

  public Object orders(long userId) {
    return db.queryForList(
      "select id,package_id,amount_cents,stars,status,created_at from recharge_order where user_id=? order by created_at desc limit 100",
      userId
    );
  }

  @Transactional
  public long settle(UUID orderId, long userId, String providerRef) {
    var orders = db.queryForList(
      "select user_id,stars,status from recharge_order where id=? for update",
      orderId
    );
    if (orders.isEmpty()) throw new ApiException(
      HttpStatus.NOT_FOUND,
      "ORDER_NOT_FOUND",
      "订单不存在"
    );
    var order = orders.getFirst();
    if (((Number) order.get("user_id")).longValue() != userId) throw new ApiException(
      HttpStatus.FORBIDDEN,
      "FORBIDDEN",
      "订单不属于当前用户"
    );
    if ("PAID".equals(order.get("status"))) return wallet.balance(userId);
    if (!"PENDING".equals(order.get("status"))) throw new ApiException(
      HttpStatus.CONFLICT,
      "INVALID_STATE",
      "订单状态不允许付款"
    );
    long stars = ((Number) order.get("stars")).longValue();
    long balance = wallet.change(userId, stars, "RECHARGE", "recharge:" + orderId);
    db.update(
      "update recharge_order set status='PAID',provider_ref=? where id=?",
      providerRef,
      orderId
    );
    return balance;
  }
}
