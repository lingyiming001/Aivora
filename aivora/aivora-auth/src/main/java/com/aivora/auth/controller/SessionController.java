package com.aivora.auth.controller;

import com.aivora.common.CurrentUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/me")
public class SessionController {

  private final CurrentUser current;
  private final JdbcTemplate db;

  public SessionController(CurrentUser current, JdbcTemplate db) {
    this.current = current;
    this.db = db;
  }

  @GetMapping
  public Object me() {
    var result = db.queryForMap(
      "select id,role,nickname,avatar_url,bio,membership_tier from app_user where id=? and status='ACTIVE'",
      current.id()
    );
    if ("ADMIN".equals(result.get("role"))) {
      var menus = current.menus();
      result.put("menus", menus);
      result.put(
        "menuCatalog",
        current
          .menuCatalog()
          .stream()
          .filter(m -> menus.contains(m.get("menu_key")))
          .toList()
      );
      result.put("superAdmin", current.superAdmin());
    }
    return result;
  }
}
