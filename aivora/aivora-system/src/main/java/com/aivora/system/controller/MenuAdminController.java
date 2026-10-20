package com.aivora.system.controller;

import com.aivora.common.*;
import com.aivora.system.dto.AdminMenuDTO;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/permissions")
public class MenuAdminController {

  private final JdbcTemplate db;
  private final CurrentUser current;

  public MenuAdminController(JdbcTemplate db, CurrentUser current) {
    this.db = db;
    this.current = current;
  }

  @GetMapping("/catalog")
  public Object catalog() {
    current.admin();
    return current.menuCatalog();
  }

  @PostMapping
  @Transactional
  public void create(@Valid @RequestBody AdminMenuDTO request) {
    save(null, request);
  }

  @PutMapping("/{key}")
  @Transactional
  public void update(@PathVariable String key, @Valid @RequestBody AdminMenuDTO request) {
    save(key, request);
  }

  private void save(String key, AdminMenuDTO r) {
    current.admin();
    db.queryForList("select pg_advisory_xact_lock(801238)");
    if (key != null && !key.equals(r.key())) throw new ApiException(
      HttpStatus.BAD_REQUEST,
      "IMMUTABLE_MENU_KEY",
      "权限标识不可修改"
    );
    if (key == null) {
      if (
        Set.of(
          "login",
          "characters",
          "providers",
          "routes",
          "recharge",
          "wallet",
          "account"
        ).contains(r.key())
      ) throw new ApiException(
        HttpStatus.BAD_REQUEST,
        "RESERVED_MENU_KEY",
        "此权限标识为系统保留标识"
      );
      if (
        !db.queryForList("select menu_key from admin_menu where menu_key=?", r.key()).isEmpty()
      ) throw new ApiException(HttpStatus.CONFLICT, "MENU_EXISTS", "权限标识已存在");
      db.update(
        "insert into admin_menu(menu_key,name,group_name,path,description,sort_order,builtin) values (?,?,?,?,?,?,?)",
        r.key(),
        r.name().trim(),
        r.groupName().trim(),
        r.key().equals("overview") ? "/admin" : "/admin/" + r.key(),
        Objects.toString(r.description(), ""),
        r.sortOrder(),
        CurrentUser.MENUS.containsKey(r.key())
      );
    } else if (
      db.update(
        "update admin_menu set name=?,group_name=?,description=?,sort_order=?,updated_at=now() where menu_key=?",
        r.name().trim(),
        r.groupName().trim(),
        Objects.toString(r.description(), ""),
        r.sortOrder(),
        key
      ) != 1
    ) throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "菜单不存在");
    audit(key == null ? "MENU_CREATE" : "MENU_UPDATE", r.key());
  }

  @DeleteMapping("/{key}")
  @Transactional
  public void delete(@PathVariable String key) {
    current.admin();
    db.queryForList("select pg_advisory_xact_lock(801238)");
    var rows = db.queryForList(
      "select system_only from admin_menu where menu_key=? for update",
      key
    );
    if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "菜单不存在");
    if (Boolean.TRUE.equals(rows.getFirst().get("system_only"))) throw new ApiException(
      HttpStatus.CONFLICT,
      "PROTECTED_MENU",
      "系统授权管理菜单不可删除"
    );
    db.update("delete from admin_role_permission where menu_key=?", key);
    db.update("delete from admin_menu_permission where menu_key=?", key);
    db.update("delete from admin_menu where menu_key=?", key);
    audit("MENU_DELETE", key);
  }

  private void audit(String action, String key) {
    db.update(
      "insert into audit_log(actor_id,action,subject) values (?,?,?)",
      current.id(),
      action,
      "menu:" + key
    );
  }
}
