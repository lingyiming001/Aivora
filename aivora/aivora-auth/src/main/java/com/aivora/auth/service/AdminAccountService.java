package com.aivora.auth.service;

import com.aivora.auth.dto.*;
import com.aivora.common.*;
import com.aivora.common.util.AvatarValidator;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAccountService {

  private final JdbcTemplate db;
  private final CurrentUser current;
  private final PasswordEncoder passwords;
  private final PhoneOtpService otp;

  public AdminAccountService(
    JdbcTemplate db,
    CurrentUser current,
    PasswordEncoder passwords,
    PhoneOtpService otp
  ) {
    this.db = db;
    this.current = current;
    this.passwords = passwords;
    this.otp = otp;
  }

  public Object profile() {
    current.adminIdentity();
    var row = db.queryForMap(
      "select id,email,username,nickname,avatar_url,bio,phone,admin_super from app_user where id=?",
      current.id()
    );
    row.put("smsAvailable", otp.available());
    row.put("developmentSms", otp.available());
    return row;
  }

  @Transactional
  public void profile(AdminProfileDTO req) {
    current.adminIdentity();
    db.update(
      "update app_user set nickname=?,bio=?,avatar_url=? where id=?",
      req.nickname().trim(),
      req.bio(),
      AvatarValidator.validateAvatar(req.avatarUrl()),
      current.id()
    );
    audit("ADMIN_PROFILE_UPDATE", current.id());
  }

  private Map<String, Object> locked() {
    current.adminIdentity();
    return db.queryForMap(
      "select password_hash,phone,session_version,admin_password_failures,admin_password_window>now()-interval '15 minutes' as recent from app_user where id=? for update",
      current.id()
    );
  }

  private void password(Map<String, Object> row, String input) {
    int attempts = Boolean.TRUE.equals(row.get("recent"))
      ? ((Number) row.get("admin_password_failures")).intValue()
      : 0;
    if (attempts >= 5) throw new ApiException(
      HttpStatus.TOO_MANY_REQUESTS,
      "PASSWORD_COOLDOWN",
      "验证密码失败次数过多，请15分钟后重试"
    );
    boolean valid = false;
    try {
      valid = passwords.matches(input, (String) row.get("password_hash"));
    } catch (IllegalArgumentException ignored) {}
    if (!valid) {
      db.update(
        "update app_user set admin_password_failures=?,admin_password_window=case when ? then admin_password_window else now() end where id=?",
        attempts + 1,
        Boolean.TRUE.equals(row.get("recent")),
        current.id()
      );
      throw new ApiException(HttpStatus.BAD_REQUEST, "INCORRECT_PASSWORD", "当前密码不正确");
    }
    db.update(
      "update app_user set admin_password_failures=0,admin_password_window=null where id=?",
      current.id()
    );
  }

  private void strength(String password) {
    if (
      password.length() < 8 ||
      password.length() > 64 ||
      password.getBytes(StandardCharsets.UTF_8).length > 72 ||
      !password.chars().anyMatch(Character::isLetter) ||
      !password.chars().anyMatch(Character::isDigit)
    ) throw new ApiException(
      HttpStatus.BAD_REQUEST,
      "WEAK_PASSWORD",
      "密码须为8至64位，包含字母和数字，且不超过72字节"
    );
  }

  private void revoke(long id) {
    db.update("update app_user set session_version=session_version+1 where id=?", id);
    db.update("delete from refresh_token where user_id=?", id);
    db.update(
      "update admin_phone_otp set consumed_at=now() where user_id=? and consumed_at is null",
      id
    );
  }

  @Transactional(noRollbackFor = ApiException.class)
  public void changePassword(AdminPasswordDTO req) {
    var row = locked();
    password(row, req.currentPassword());
    strength(req.newPassword());
    if (
      passwords.matches(req.newPassword(), (String) row.get("password_hash"))
    ) throw new ApiException(HttpStatus.BAD_REQUEST, "SAME_PASSWORD", "新密码不能与当前密码相同");
    db.update(
      "update app_user set password_hash=? where id=?",
      passwords.encode(req.newPassword()),
      current.id()
    );
    revoke(current.id());
    audit("ADMIN_PASSWORD_UPDATE", current.id());
  }

  private void freePhone(String phone) {
    if (
      db.queryForObject(
        "select count(*) from app_user where phone=? and id<>?",
        Integer.class,
        phone,
        current.id()
      ) > 0
    ) throw new ApiException(HttpStatus.CONFLICT, "PHONE_EXISTS", "此手机号已绑定其他账号");
  }

  @Transactional(noRollbackFor = ApiException.class)
  public Object sendCode(AdminPhoneCodeDTO req, String ip) {
    var row = locked();
    password(row, req.currentPassword());
    String phone = req.purpose().equals("OLD")
      ? (String) row.get("phone")
      : PhoneOtpService.phone(req.phone());
    if (phone == null) throw new ApiException(
      HttpStatus.BAD_REQUEST,
      "PHONE_UNBOUND",
      "账号尚未绑定手机号"
    );
    if (req.purpose().equals("NEW")) {
      freePhone(phone);
      if (phone.equals(row.get("phone"))) throw new ApiException(
        HttpStatus.BAD_REQUEST,
        "SAME_PHONE",
        "新手机号不能与当前手机号相同"
      );
    }
    return otp.sendBinding(
      current.id(),
      phone,
      req.purpose(),
      ip,
      ((Number) row.get("session_version")).longValue()
    );
  }

  @Transactional(noRollbackFor = ApiException.class)
  public void bindPhone(AdminPhoneBindDTO req) {
    var row = locked();
    password(row, req.currentPassword());
    String phone = PhoneOtpService.phone(req.phone());
    freePhone(phone);
    if (phone.equals(row.get("phone"))) throw new ApiException(
      HttpStatus.BAD_REQUEST,
      "SAME_PHONE",
      "新手机号不能与当前手机号相同"
    );
    long version = ((Number) row.get("session_version")).longValue();
    UUID old = null;
    if (row.get("phone") != null) old = otp.verifyBinding(
      current.id(),
      (String) row.get("phone"),
      "OLD",
      req.oldCode(),
      version
    );
    UUID next = otp.verifyBinding(current.id(), phone, "NEW", req.newCode(), version);
    db.update("update app_user set phone=? where id=?", phone, current.id());
    if (old != null) otp.consumeBinding(old);
    otp.consumeBinding(next);
    revoke(current.id());
    audit("ADMIN_PHONE_UPDATE", current.id());
  }

  private void superAdmin() {
    current.adminIdentity();
    if (!current.superAdmin()) throw new ApiException(
      HttpStatus.FORBIDDEN,
      "SUPER_ADMIN_REQUIRED",
      "仅超级管理员可以管理管理员账号"
    );
  }

  @Transactional
  public long create(AdminCreateAccountDTO req) {
    superAdmin();
    strength(req.password());
    db.queryForList("select pg_advisory_xact_lock(801239)");
    String email = req.email().trim().toLowerCase(Locale.ROOT);
    if (
      db.queryForObject(
        "select count(*) from app_user where lower(email)=?",
        Integer.class,
        email
      ) > 0
    ) throw new ApiException(
      HttpStatus.CONFLICT,
      "EMAIL_EXISTS",
      "此邮箱已存在，请选择已有用户设为管理员或更换邮箱"
    );
    for (long role : new HashSet<>(req.roleIds()))
      if (
        db
          .queryForList("select id from admin_role where id=? and enabled for update", role)
          .isEmpty()
      ) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ROLE", "请选择启用的权限角色");
    long id = db.queryForObject(
      "insert into app_user(email,password_hash,nickname,role,status,admin_super) values (?,?,?,'ADMIN',?,false) returning id",
      Long.class,
      email,
      passwords.encode(req.password()),
      req.nickname().trim(),
      req.enabled() ? "ACTIVE" : "DISABLED"
    );
    db.update("insert into wallet(user_id) values (?)", id);
    for (long role : new HashSet<>(req.roleIds()))
      db.update("insert into admin_user_role(user_id,role_id) values (?,?)", id, role);
    audit("ADMIN_CREATE", id);
    return id;
  }

  private void target(long id) {
    superAdmin();
    var rows = db.queryForList(
      "select admin_super from app_user where id=? and role='ADMIN' for update",
      id
    );
    if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "管理员不存在");
    if (
      id == current.id() || Boolean.TRUE.equals(rows.getFirst().get("admin_super"))
    ) throw new ApiException(
      HttpStatus.CONFLICT,
      "PROTECTED_ADMIN",
      "不能停用或强制下线自己及超级管理员账号"
    );
  }

  @Transactional
  public void status(long id, String status) {
    target(id);
    if (!Set.of("ACTIVE", "DISABLED").contains(status)) throw new ApiException(
      HttpStatus.BAD_REQUEST,
      "INVALID_STATUS",
      "账号状态无效"
    );
    db.update("update app_user set status=? where id=?", status, id);
    revoke(id);
    audit("ADMIN_STATUS_" + status, id);
  }

  @Transactional
  public void kick(long id) {
    target(id);
    revoke(id);
    audit("ADMIN_FORCE_LOGOUT", id);
  }

  private void audit(String action, long id) {
    db.update(
      "insert into audit_log(actor_id,action,subject) values (?,?,?)",
      current.id(),
      action,
      "admin:" + id
    );
  }
}
