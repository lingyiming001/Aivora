package com.aivora.system.controller;

import com.aivora.common.*;
import com.aivora.system.dto.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AccessController {

  private final JdbcTemplate db;
  private final CurrentUser current;
  private final com.aivora.auth.service.AdminAccountService accounts;

  public AccessController(
    JdbcTemplate db,
    CurrentUser current,
    com.aivora.auth.service.AdminAccountService accounts
  ) {
    this.db = db;
    this.accounts = accounts;
    this.current = current;
  }

  @GetMapping("/access")
  public Object list() {
    current.admin();
    var admins = db.queryForList(
      "select id,nickname,email,status,admin_super,case when phone is null then null else left(phone,3)||'****'||right(phone,4) end as phone,(select count(*) from refresh_token r where r.user_id=app_user.id and r.revoked_at is null and r.expires_at>now()) as session_count from app_user where role='ADMIN' order by id"
    );
    for (var admin : admins)
      admin.put(
        "permissions",
        db.queryForList(
          "select distinct p.menu_key from admin_user_role u join admin_role r on r.id=u.role_id join admin_role_permission p on p.role_id=r.id where u.user_id=? and r.enabled order by p.menu_key",
          String.class,
          admin.get("id")
        )
      );
    for (var admin : admins)
      admin.put(
        "role_ids",
        db.queryForList(
          "select role_id from admin_user_role where user_id=?",
          Long.class,
          admin.get("id")
        )
      );
    return Map.of("menus", current.permissionNames(), "admins", admins, "currentId", current.id());
  }

  @PostMapping("/access")
  public Object create(@Valid @RequestBody com.aivora.auth.dto.AdminCreateAccountDTO req) {
    current.admin();
    return Map.of("id", accounts.create(req));
  }

  @PutMapping("/access/{id}/status")
  public void adminStatus(@PathVariable long id, @Valid @RequestBody AccessStatusDTO req) {
    current.admin();
    accounts.status(id, req.status());
  }

  @PostMapping("/access/{id}/logout")
  public void kick(@PathVariable long id) {
    current.admin();
    accounts.kick(id);
  }

  @PutMapping("/users/{id}/status")
  @Transactional
  public void status(@PathVariable long id, @Valid @RequestBody AccessStatusDTO s) {
    current.admin();
    if (
      db.update("update app_user set status=? where id=? and role='USER'", s.status(), id) != 1
    ) throw new ApiException(HttpStatus.CONFLICT, "PROTECTED_ACCOUNT", "仅可启停普通用户");
    audit("USER_STATUS_UPDATE", "user:" + id + " status:" + s.status());
  }

  @PostMapping("/access/{id}/promote")
  @Transactional
  public void promote(@PathVariable long id) {
    current.admin();
    if (
      db.update(
        "update app_user set role='ADMIN',session_version=session_version+1 where id=? and role='USER' and status='ACTIVE'",
        id
      ) != 1
    ) throw new ApiException(HttpStatus.CONFLICT, "INVALID_USER", "请选择启用的普通用户");
    db.update("delete from refresh_token where user_id=?", id);
    audit("ADMIN_PROMOTE", "user:" + id);
  }

  @PostMapping("/access/{id}/demote")
  @Transactional
  public void demote(@PathVariable long id) {
    current.admin();
    if (
      db.update(
        "update app_user set role='USER',session_version=session_version+1 where id=? and role='ADMIN' and admin_super=false",
        id
      ) != 1
    ) throw new ApiException(HttpStatus.CONFLICT, "PROTECTED_ADMIN", "超级管理员不能降级");
    db.update("delete from admin_user_role where user_id=?", id);
    db.update("delete from refresh_token where user_id=?", id);
    audit("ADMIN_DEMOTE", "user:" + id);
  }

  private void audit(String a, String s) {
    db.update("insert into audit_log(actor_id,action,subject) values (?,?,?)", current.id(), a, s);
  }
}
