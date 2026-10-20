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
public class RoleController {

  private final JdbcTemplate db;
  private final CurrentUser current;

  public RoleController(JdbcTemplate db, CurrentUser current) {
    this.db = db;
    this.current = current;
  }

  @GetMapping("/roles")
  public Object list() {
    current.admin();
    var rows = db.queryForList(
      "select r.*,(select count(*) from admin_user_role u where u.role_id=r.id) as member_count from admin_role r order by r.id"
    );
    for (var r : rows)
      r.put(
        "menus",
        db.queryForList(
          "select menu_key from admin_role_permission where role_id=?",
          String.class,
          r.get("id")
        )
      );
    return rows;
  }

  @GetMapping("/permissions")
  public Object permissions() {
    current.admin();
    return current.permissionNames();
  }

  @PostMapping("/roles")
  @Transactional
  public void create(@Valid @RequestBody RoleRoleDTO r) {
    save(null, r);
  }

  @PutMapping("/roles/{id}")
  @Transactional
  public void edit(@PathVariable long id, @Valid @RequestBody RoleRoleDTO r) {
    save(id, r);
  }

  private void save(Long id, RoleRoleDTO r) {
    current.admin();
    db.queryForList("select pg_advisory_xact_lock(801238)");
    if (
      !current.permissionNames().keySet().containsAll(r.menus()) ||
      r
        .menus()
        .stream()
        .anyMatch(Set.of("access", "roles", "permissions")::contains)
    ) throw new ApiException(
      HttpStatus.BAD_REQUEST,
      "INVALID_PERMISSION",
      "系统授权管理仅限超级管理员"
    );
    if (
      db.queryForObject(
        "select count(*) from admin_role where code=? and (cast(? as bigint) is null or id<>?)",
        Integer.class,
        r.code(),
        id,
        id
      ) > 0
    ) throw new ApiException(HttpStatus.CONFLICT, "ROLE_CODE_EXISTS", "角色编码已存在");
    if (id == null) id = db.queryForObject(
      "insert into admin_role(code,name,description,enabled) values (?,?,?,?) returning id",
      Long.class,
      r.code(),
      r.name().trim(),
      r.description(),
      r.enabled()
    );
    else if (
      db.update(
        "update admin_role set code=?,name=?,description=?,enabled=? where id=?",
        r.code(),
        r.name().trim(),
        r.description(),
        r.enabled(),
        id
      ) != 1
    ) throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "角色不存在");
    db.update("delete from admin_role_permission where role_id=?", id);
    for (String key : new HashSet<>(r.menus()))
      db.update("insert into admin_role_permission values (?,?)", id, key);
    audit("ROLE_UPDATE", "role:" + id);
  }

  @DeleteMapping("/roles/{id}")
  @Transactional
  public void delete(@PathVariable long id) {
    current.admin();
    db.queryForList("select id from admin_role where id=? for update", id);
    if (
      db.queryForObject("select count(*) from admin_user_role where role_id=?", Integer.class, id) >
      0
    ) throw new ApiException(HttpStatus.CONFLICT, "ROLE_IN_USE", "角色已分配，请先移除成员关联");
    if (db.update("delete from admin_role where id=?", id) != 1) throw new ApiException(
      HttpStatus.NOT_FOUND,
      "NOT_FOUND",
      "角色不存在"
    );
    audit("ROLE_DELETE", "role:" + id);
  }

  @PutMapping("/access/{id}/roles")
  @Transactional
  public void assign(@PathVariable long id, @Valid @RequestBody RoleAssignmentDTO a) {
    current.admin();
    var users = db.queryForList(
      "select admin_super from app_user where id=? and role='ADMIN' for update",
      id
    );
    if (users.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "管理员不存在");
    if (Boolean.TRUE.equals(users.getFirst().get("admin_super"))) throw new ApiException(
      HttpStatus.CONFLICT,
      "PROTECTED_ADMIN",
      "超级管理员保留全部权限，无需分配业务角色"
    );
    for (long role : new HashSet<>(a.roleIds()))
      if (
        db
          .queryForList("select id from admin_role where id=? and enabled for update", role)
          .isEmpty()
      ) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ROLE", "请选择启用的角色");
    db.update("delete from admin_user_role where user_id=?", id);
    for (long role : new HashSet<>(a.roleIds()))
      db.update("insert into admin_user_role values (?,?)", id, role);
    audit("ADMIN_ROLE_ASSIGN", "admin:" + id);
  }

  private void audit(String a, String s) {
    db.update("insert into audit_log(actor_id,action,subject) values (?,?,?)", current.id(), a, s);
  }
}
