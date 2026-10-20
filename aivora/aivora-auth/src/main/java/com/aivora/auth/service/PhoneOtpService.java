package com.aivora.auth.service;

import com.aivora.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PhoneOtpService {

  private final JdbcTemplate db;
  private final AuthService auth;
  private final boolean development;
  private final String secret;
  private final SecureRandom random = new SecureRandom();

  public PhoneOtpService(
    JdbcTemplate db,
    AuthService auth,
    Environment env,
    @Value("${aivora.jwt-secret}") String secret
  ) {
    this.db = db;
    this.auth = auth;
    this.development = env.acceptsProfiles(Profiles.of("dev"));
    this.secret = secret;
  }

  public boolean available() {
    return development;
  }

  public record Sent(int expiresIn, int retryAfter, String developmentCode) {}

  static String phone(String value) {
    String p = value == null ? "" : value.trim();
    if (!p.matches("1[3-9][0-9]{9}")) throw new ApiException(
      HttpStatus.BAD_REQUEST,
      "INVALID_PHONE",
      "请输入正确的中国大陆手机号"
    );
    return p;
  }

  private void requireDelivery() {
    if (!development) throw new ApiException(
      HttpStatus.SERVICE_UNAVAILABLE,
      "SMS_NOT_CONFIGURED",
      "短信服务尚未配置，请使用账号密码登录"
    );
  }

  @Transactional
  public Sent send(String input, boolean admin, String ip) {
    requireDelivery();
    String phone = phone(input),
      portal = admin ? "ADMIN" : "USER";
    db.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))", phone);
    db.queryForList("select pg_advisory_xact_lock(hashtextextended(?,1))", ip);
    if (
      db.queryForObject(
        "select count(*) from login_otp where phone=? and portal=? and created_at>now()-interval '60 seconds'",
        Integer.class,
        phone,
        portal
      ) > 0
    ) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "OTP_COOLDOWN", "请稍候再获取验证码");
    if (
      db.queryForObject(
        "select count(*) from login_otp where request_ip=? and created_at>now()-interval '1 hour'",
        Integer.class,
        ip
      ) >= 20
    ) throw new ApiException(
      HttpStatus.TOO_MANY_REQUESTS,
      "OTP_RATE_LIMIT",
      "验证码请求过于频繁，请稍后再试"
    );
    if (
      admin &&
      db.queryForObject(
        "select count(*) from app_user where phone=? and role='ADMIN' and status='ACTIVE'",
        Integer.class,
        phone
      ) == 0
    ) throw new ApiException(
      HttpStatus.BAD_REQUEST,
      "ADMIN_PHONE_UNBOUND",
      "该手机号未绑定有效管理员账号"
    );
    String code = String.format("%06d", random.nextInt(1_000_000));
    UUID id = UUID.randomUUID();
    db.update(
      "update login_otp set consumed_at=now() where phone=? and portal=? and consumed_at is null",
      phone,
      portal
    );
    db.update(
      "insert into login_otp(id,phone,portal,code_hash,request_ip,expires_at) values (?,?,?,?,?,now()+interval '5 minutes')",
      id,
      phone,
      portal,
      digest(id, code),
      ip
    );
    db.update("delete from login_otp where created_at<now()-interval '1 day'");
    return new Sent(300, 60, code);
  }

  // Incorrect attempts must commit, otherwise an exception would reset the attempt counter.
  @Transactional(noRollbackFor = ApiException.class)
  public AuthService.Tokens login(String input, String code, boolean admin) {
    requireDelivery();
    String phone = phone(input),
      portal = admin ? "ADMIN" : "USER";
    db.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))", phone);
    var rows = db.queryForList(
      "select id,code_hash,attempts,consumed_at,expires_at>now() as valid from login_otp where phone=? and portal=? order by created_at desc limit 1 for update",
      phone,
      portal
    );
    if (rows.isEmpty()) throw invalid();
    var row = rows.getFirst();
    UUID id = (UUID) row.get("id");
    if (
      row.get("consumed_at") != null ||
      !Boolean.TRUE.equals(row.get("valid")) ||
      ((Number) row.get("attempts")).intValue() >= 5
    ) throw invalid();
    db.update("update login_otp set attempts=attempts+1 where id=?", id);
    if (
      code == null ||
      !MessageDigest.isEqual(
        digest(id, code).getBytes(StandardCharsets.UTF_8),
        String.valueOf(row.get("code_hash")).getBytes(StandardCharsets.UTF_8)
      )
    ) throw invalid();
    db.update("update login_otp set consumed_at=now() where id=?", id);
    return auth.phoneLogin(phone, admin);
  }

  @Transactional(noRollbackFor = ApiException.class)
  public Sent sendBinding(long user, String input, String purpose, String ip, long version) {
    requireDelivery();
    String phone = phone(input);
    db.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))", phone);
    if (
      db.queryForObject(
        "select count(*) from admin_phone_otp where user_id=? and purpose=? and created_at>now()-interval '60 seconds'",
        Integer.class,
        user,
        purpose
      ) > 0
    ) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "OTP_COOLDOWN", "请60秒后再获取验证码");
    if (
      db.queryForObject(
        "select count(*) from admin_phone_otp where (user_id=? or request_ip=?) and created_at>now()-interval '1 hour'",
        Integer.class,
        user,
        ip
      ) >= 20
    ) throw new ApiException(
      HttpStatus.TOO_MANY_REQUESTS,
      "OTP_RATE_LIMIT",
      "验证码请求过于频繁，请稍后再试"
    );
    String code = String.format("%06d", random.nextInt(1_000_000));
    UUID id = UUID.randomUUID();
    db.update(
      "update admin_phone_otp set consumed_at=now() where user_id=? and purpose=? and consumed_at is null",
      user,
      purpose
    );
    db.update(
      "insert into admin_phone_otp(id,user_id,phone,purpose,session_version,code_hash,request_ip,expires_at) values (?,?,?,?,?,?,?,now()+interval '5 minutes')",
      id,
      user,
      phone,
      purpose,
      version,
      digest(id, code),
      ip
    );
    db.update("delete from admin_phone_otp where created_at<now()-interval '1 day'");
    return new Sent(300, 60, code);
  }

  UUID verifyBinding(long user, String phone, String purpose, String code, long version) {
    requireDelivery();
    var rows = db.queryForList(
      "select id,code_hash,attempts,consumed_at,expires_at>now() as valid from admin_phone_otp where user_id=? and phone=? and purpose=? and session_version=? order by created_at desc limit 1 for update",
      user,
      phone,
      purpose,
      version
    );
    if (rows.isEmpty()) throw invalid();
    var row = rows.getFirst();
    UUID id = (UUID) row.get("id");
    if (
      row.get("consumed_at") != null ||
      !Boolean.TRUE.equals(row.get("valid")) ||
      ((Number) row.get("attempts")).intValue() >= 5
    ) throw invalid();
    db.update("update admin_phone_otp set attempts=attempts+1 where id=?", id);
    if (
      code == null ||
      !MessageDigest.isEqual(
        digest(id, code).getBytes(StandardCharsets.UTF_8),
        String.valueOf(row.get("code_hash")).getBytes(StandardCharsets.UTF_8)
      )
    ) throw invalid();
    return id;
  }

  void consumeBinding(UUID id) {
    db.update("update admin_phone_otp set consumed_at=now() where id=?", id);
  }

  private ApiException invalid() {
    return new ApiException(
      HttpStatus.UNAUTHORIZED,
      "INVALID_OTP",
      "验证码错误、已失效或尝试次数过多，请重新获取"
    );
  }

  String digest(UUID id, String code) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(
        mac.doFinal((id + ":" + code).getBytes(StandardCharsets.UTF_8))
      );
    } catch (GeneralSecurityException ex) {
      throw new IllegalStateException(ex);
    }
  }
}
