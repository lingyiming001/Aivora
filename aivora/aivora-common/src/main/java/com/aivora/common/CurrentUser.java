package com.aivora.common;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {

  private final JdbcTemplate db;

  public CurrentUser(JdbcTemplate db) {
    this.db = db;
  }

  public long id() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof Long id)) throw new ApiException(
      HttpStatus.UNAUTHORIZED,
      "AUTH_REQUIRED",
      "请先登录"
    );
    return id;
  }

  public long optionalId() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    return auth != null && auth.getPrincipal() instanceof Long id ? id : -1L;
  }

  public boolean guest() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    return (
      auth != null &&
      auth
        .getAuthorities()
        .stream()
        .anyMatch(a -> "ROLE_GUEST".equals(a.getAuthority()))
    );
  }

  public static final java.util.Map<String, String> MENUS = java.util.Map.ofEntries(
    java.util.Map.entry("overview", "运营概览"),
    java.util.Map.entry("users", "用户管理"),
    java.util.Map.entry("review", "角色卡管理"),
    java.util.Map.entry("orders", "充值订单"),
    java.util.Map.entry("ledger", "钱包流水"),
    java.util.Map.entry("reconciliation", "异常对账"),
    java.util.Map.entry("audit", "操作日志"),
    java.util.Map.entry("models", "模型与路由"),
    java.util.Map.entry("status", "系统状态"),
    java.util.Map.entry("access", "管理员管理"),
    java.util.Map.entry("roles", "角色管理"),
    java.util.Map.entry("permissions", "菜单权限")
  );

  public boolean superAdmin() {
    return Boolean.TRUE.equals(
      db.queryForObject("select admin_super from app_user where id=?", Boolean.class, id())
    );
  }

  public java.util.List<String> menus() {
    if (superAdmin()) return new java.util.ArrayList<>(permissionNames().keySet());
    return db.queryForList(
      "select distinct p.menu_key from admin_user_role u join admin_role r on r.id=u.role_id join admin_role_permission p on p.role_id=r.id join admin_menu m on m.menu_key=p.menu_key where u.user_id=? and r.enabled and not m.system_only",
      String.class,
      id()
    );
  }

  public java.util.Map<String, String> permissionNames() {
    var result = new java.util.LinkedHashMap<String, String>();
    for (var row : db.queryForList(
      "select menu_key,name from admin_menu order by group_name,sort_order,menu_key"
    ))
      result.put((String) row.get("menu_key"), (String) row.get("name"));
    return result;
  }

  public java.util.List<java.util.Map<String, Object>> menuCatalog() {
    return db.queryForList("select * from admin_menu order by group_name,sort_order,menu_key");
  }

  public void adminIdentity() {
    String role = db.queryForObject(
      "select role from app_user where id=? and status='ACTIVE'",
      String.class,
      id()
    );
    if (!"ADMIN".equals(role)) throw new ApiException(
      HttpStatus.FORBIDDEN,
      "FORBIDDEN",
      "需要管理员权限"
    );
  }

  public void admin() {
    adminIdentity();
    var attrs = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
    if (attrs instanceof org.springframework.web.context.request.ServletRequestAttributes a) {
      String path = a.getRequest().getRequestURI().replaceFirst("^/api/admin/?", "");
      String part = path.split("/")[0];
      String menu = switch (part) {
        case "characters" -> "review";
        case "providers", "models", "routes" -> "models";
        case "wallet" -> "ledger";
        case "recharge" -> "orders";
        default -> part;
      };
      if (
        !superAdmin() &&
        (java.util.Set.of("access", "roles", "permissions").contains(menu) ||
          !menus().contains(menu))
      ) throw new ApiException(HttpStatus.FORBIDDEN, "MENU_FORBIDDEN", "没有此后台功能的权限");
    }
  }
}
