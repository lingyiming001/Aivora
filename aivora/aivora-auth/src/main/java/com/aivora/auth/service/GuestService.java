package com.aivora.auth.service;

import com.aivora.common.ApiException;
import jakarta.servlet.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GuestService {

  public static final String COOKIE = "aivora_guest";
  private final JdbcTemplate db;
  private final PasswordEncoder encoder;
  private final long allowance;
  private final String secret;

  public GuestService(
    JdbcTemplate db,
    PasswordEncoder encoder,
    @Value("${aivora.guest-stars:10}") long allowance,
    @Value("${aivora.jwt-secret}") String secret
  ) {
    this.db = db;
    this.encoder = encoder;
    this.allowance = Math.max(0, allowance);
    this.secret = secret;
  }

  public Long identify(HttpServletRequest request) {
    String value = cookie(request);
    if (value == null) return null;
    var rows = db.queryForList(
      "select g.user_id from guest_session g join app_user u on u.id=g.user_id where g.token_hash=? and g.expires_at>now() and u.role='GUEST' and u.status='ACTIVE'",
      hash(value)
    );
    return rows.isEmpty() ? null : ((Number) rows.getFirst().get("user_id")).longValue();
  }

  public Object status(HttpServletRequest request) {
    Long id = identify(request);
    return id == null ? Map.of("active", false, "balance", 0, "allowance", allowance) : state(id);
  }

  @Transactional
  public Object start(HttpServletRequest request, HttpServletResponse response) {
    Long id = identify(request);
    if (id != null) return state(id);
    String ip = hash("ip:" + request.getRemoteAddr());
    db.queryForList("select pg_advisory_xact_lock(hashtextextended(?,2))", ip);
    if (
      db.queryForObject(
        "select count(*) from guest_session where ip_hash=? and created_at>now()-interval '24 hours'",
        Integer.class,
        ip
      ) >= 3
    ) throw new ApiException(
      HttpStatus.PAYMENT_REQUIRED,
      "GUEST_QUOTA_EXHAUSTED",
      "今日游客体验次数已用完，登录后继续聊天"
    );
    id = db.queryForObject(
      "insert into app_user(email,password_hash,nickname,role) values (?,?,?,'GUEST') returning id",
      Long.class,
      UUID.randomUUID() + "@guest.invalid",
      encoder.encode(UUID.randomUUID().toString()),
      "游客"
    );
    db.update("insert into wallet(user_id,balance) values (?,?)", id, allowance);
    if (allowance > 0) db.update(
      "insert into wallet_ledger(user_id,delta,balance_after,reason,reference_key) values (?,?,?,'GUEST_GIFT',?)",
      id,
      allowance,
      allowance,
      "guest-gift:" + id
    );
    String token = UUID.randomUUID() + "." + UUID.randomUUID();
    db.update(
      "insert into guest_session(token_hash,user_id,ip_hash,expires_at) values (?,?,?,now()+interval '7 days')",
      hash(token),
      id,
      ip
    );
    response.addHeader(
      "Set-Cookie",
      org.springframework.http.ResponseCookie.from(COOKIE, token)
        .httpOnly(true)
        .secure(request.isSecure())
        .sameSite("Lax")
        .path("/api")
        .maxAge(Duration.ofDays(7))
        .build()
        .toString()
    );
    return state(id);
  }

  private Object state(long id) {
    return Map.of(
      "active",
      true,
      "balance",
      db.queryForObject("select balance from wallet where user_id=?", Long.class, id),
      "allowance",
      allowance
    );
  }

  private String cookie(HttpServletRequest request) {
    if (request.getCookies() != null) for (var c : request.getCookies())
      if (COOKIE.equals(c.getName()) && c.getValue().length() < 200) return c.getValue();
    return null;
  }

  private String hash(String input) {
    try {
      return HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(
          (secret + ":" + input).getBytes(StandardCharsets.UTF_8)
        )
      );
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
