package com.aivora.auth.service;

import com.aivora.common.ApiException;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  public record Tokens(String accessToken, String refreshToken, long expiresAt) {
    public Tokens(String accessToken, String refreshToken) {
      this(
        accessToken,
        refreshToken,
        Instant.now()
          .plusSeconds(7L * 86400)
          .toEpochMilli()
      );
    }
  }

  private final JdbcTemplate db;
  private final PasswordEncoder encoder;
  private final Algorithm algorithm;

  public AuthService(
    JdbcTemplate db,
    PasswordEncoder encoder,
    @Value("${aivora.jwt-secret}") String secret
  ) {
    this.db = db;
    this.encoder = encoder;
    this.algorithm = Algorithm.HMAC256(secret);
  }

  @Transactional
  public Tokens register(String email, String password, String nickname) {
    return register(email, password, nickname, null);
  }

  @Transactional
  public Tokens register(String email, String password, String nickname, String username) {
    if (
      db.queryForObject(
        "select count(*) from app_user where email=?",
        Integer.class,
        email.toLowerCase()
      ) > 0
    ) throw new ApiException(HttpStatus.CONFLICT, "EMAIL_EXISTS", "邮箱已注册");
    String user = username == null || username.isBlank() ? null : username.trim().toLowerCase();
    if (
      user != null &&
      db.queryForObject("select count(*) from app_user where username=?", Integer.class, user) > 0
    ) throw new ApiException(HttpStatus.CONFLICT, "USERNAME_EXISTS", "用户名已被使用");
    Long id = db.queryForObject(
      "insert into app_user(email,password_hash,nickname,username) values (?,?,?,?) returning id",
      Long.class,
      email.toLowerCase(),
      encoder.encode(password),
      nickname,
      user
    );
    db.update("insert into wallet(user_id) values (?)", id);
    return issue(id, "USER");
  }

  @Transactional
  public Tokens login(String email, String password) {
    return login(email, password, false);
  }

  @Transactional
  public Tokens login(String email, String password, boolean admin) {
    var rows = db.query(
      "select id,password_hash,role,status from app_user where email=? or username=? or phone=? for update",
      (rs, n) -> new Object[] { rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4) },
      email.trim().toLowerCase(),
      email.trim().toLowerCase(),
      email.trim()
    );
    if (
      rows.isEmpty() || !encoder.matches(password, (String) rows.getFirst()[1])
    ) throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "账号或密码错误");
    Object[] row = rows.getFirst();
    if (!"ACTIVE".equals(row[3])) throw new ApiException(
      HttpStatus.FORBIDDEN,
      "USER_DISABLED",
      "账号已停用"
    );
    if (admin != "ADMIN".equals(row[2])) throw new ApiException(
      HttpStatus.FORBIDDEN,
      "WRONG_LOGIN_PORTAL",
      admin ? "需要管理员账号" : "管理员请使用管理后台登录"
    );
    return issue((Long) row[0], (String) row[2]);
  }

  Tokens phoneLogin(String phone, boolean admin) {
    var rows = db.queryForList(
      "select id,role,status from app_user where phone=? for update",
      phone
    );
    if (rows.isEmpty()) {
      if (admin) throw new ApiException(
        HttpStatus.FORBIDDEN,
        "WRONG_LOGIN_PORTAL",
        "需要已绑定手机号的管理员账号"
      );
      Long id = db.queryForObject(
        "insert into app_user(email,password_hash,nickname,phone) values (?,?,?,?) returning id",
        Long.class,
        UUID.randomUUID() + "@phone.invalid",
        encoder.encode(UUID.randomUUID().toString()),
        "星友" + phone.substring(7),
        phone
      );
      db.update("insert into wallet(user_id) values (?)", id);
      return issue(id, "USER");
    }
    var row = rows.getFirst();
    if (!"ACTIVE".equals(row.get("status"))) throw new ApiException(
      HttpStatus.FORBIDDEN,
      "USER_DISABLED",
      "账号已停用"
    );
    if (admin != "ADMIN".equals(row.get("role"))) throw new ApiException(
      HttpStatus.FORBIDDEN,
      "WRONG_LOGIN_PORTAL",
      admin ? "需要管理员账号" : "管理员请使用管理后台登录"
    );
    return issue(((Number) row.get("id")).longValue(), String.valueOf(row.get("role")));
  }

  @Transactional
  public Tokens refresh(String refresh) {
    String hash = sha(refresh);
    var rows = db.query(
      "select r.id,r.user_id,u.role,u.status,r.expires_at from refresh_token r join app_user u on u.id=r.user_id where r.token_hash=? and r.revoked_at is null and r.expires_at>now() for update",
      (rs, n) -> new Object[] {
        rs.getObject(1, UUID.class),
        rs.getLong(2),
        rs.getString(3),
        rs.getString(4),
        rs.getTimestamp(5).toInstant(),
      },
      hash
    );
    if (rows.isEmpty() || !"ACTIVE".equals(rows.getFirst()[3])) throw new ApiException(
      HttpStatus.UNAUTHORIZED,
      "INVALID_REFRESH",
      "刷新令牌无效"
    );
    Object[] row = rows.getFirst();
    db.update("update refresh_token set revoked_at=now() where id=?", (UUID) row[0]);
    return issue((Long) row[1], (String) row[2], (Instant) row[4]);
  }

  @Transactional
  public void logout(String refresh) {
    db.update("update refresh_token set revoked_at=now() where token_hash=?", sha(refresh));
  }

  public Long verify(String token) {
    try {
      var decoded = JWT.require(algorithm).withIssuer("aivora").build().verify(token);
      Long id = Long.valueOf(decoded.getSubject());
      Long version = decoded.getClaim("sv").asLong();
      if (version == null) version = 0L;
      if (
        db.queryForObject(
          "select count(*) from app_user where id=? and status='ACTIVE' and session_version=?",
          Integer.class,
          id,
          version
        ) == 0
      ) throw new IllegalArgumentException();
      return id;
    } catch (Exception e) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "登录令牌无效");
    }
  }

  public String verifiedRole(String token) {
    Long id = verify(token);
    String role = db.queryForObject("select role from app_user where id=?", String.class, id);
    String issuedRole = JWT.require(algorithm)
      .withIssuer("aivora")
      .build()
      .verify(token)
      .getClaim("role")
      .asString();
    if (!role.equals(issuedRole)) throw new ApiException(
      HttpStatus.UNAUTHORIZED,
      "ROLE_CHANGED",
      "权限已变更，请重新登录"
    );
    return role;
  }

  private Tokens issue(Long id, String role) {
    return issue(id, role, Instant.now().plusSeconds(7L * 86400));
  }

  private Tokens issue(Long id, String role, Instant sessionExpiry) {
    Instant now = Instant.now(),
      cap = now.plusSeconds(7L * 86400);
    Instant expires = sessionExpiry.isBefore(cap) ? sessionExpiry : cap;
    Instant accessExpiry = now.plusSeconds(900).isBefore(expires) ? now.plusSeconds(900) : expires;
    Long version = db.queryForObject(
      "select session_version from app_user where id=?",
      Long.class,
      id
    );
    String access = JWT.create()
      .withIssuer("aivora")
      .withSubject(id.toString())
      .withClaim("role", role)
      .withClaim("sv", version)
      .withExpiresAt(Date.from(accessExpiry))
      .sign(algorithm);
    String refresh = UUID.randomUUID() + "." + UUID.randomUUID();
    db.update(
      "insert into refresh_token(id,user_id,token_hash,expires_at) values (?,?,?,?)",
      UUID.randomUUID(),
      id,
      sha(refresh),
      java.sql.Timestamp.from(expires)
    );
    return new Tokens(access, refresh, expires.toEpochMilli());
  }

  private String sha(String input) {
    try {
      return HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8))
      );
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
