package com.aivora.system.controller;

import com.aivora.chat.service.CompositionService;
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
public class RouteAdminController {

  private final JdbcTemplate db;
  private final CurrentUser current;

  public RouteAdminController(JdbcTemplate db, CurrentUser current) {
    this.db = db;
    this.current = current;
  }

  @GetMapping("/routes")
  public Object list() {
    current.admin();
    var rows = db.queryForList(
      "select r.*,m.model_name,p.name as provider,(m.enabled and p.enabled and m.model_type in ('CHAT','TEXT') and not exists(select 1 from unnest(r.helper_model_ids) h(id) left join ai_model hm on hm.id=h.id left join ai_provider hp on hp.id=hm.provider_id where hm.id is null or not hm.enabled or not hp.enabled or hm.model_type not in ('CHAT','TEXT'))) as model_available from model_route_rule r join ai_model m on m.id=r.model_id join ai_provider p on p.id=m.provider_id order by r.user_tier,r.min_stars,r.priority desc"
    );
    for (var row : rows) {
      try {
        var a = (java.sql.Array) row.get("helper_model_ids");
        row.put("helper_model_ids", Arrays.asList((Long[]) a.getArray()));
      } catch (java.sql.SQLException e) {
        throw new IllegalStateException(e);
      }
    }
    return rows;
  }

  @PostMapping("/routes")
  @Transactional
  public void add(@Valid @RequestBody RouteAdminRuleDTO r) {
    save(null, r);
  }

  @PutMapping("/routes/{id}")
  @Transactional
  public void edit(@PathVariable long id, @Valid @RequestBody RouteAdminRuleDTO r) {
    save(id, r);
  }

  private void save(Long id, RouteAdminRuleDTO r) {
    current.admin();
    if (r.maxStars() != null && r.maxStars() < r.minStars()) throw new ApiException(
      HttpStatus.BAD_REQUEST,
      "INVALID_RANGE",
      "最高消耗不能小于最低消耗"
    );
    if (
      db.queryForObject(
        "select count(*) from ai_model m join ai_provider p on p.id=m.provider_id where m.id=? and m.model_type in ('CHAT','TEXT') and (?=false or (m.enabled and p.enabled))",
        Integer.class,
        r.modelId(),
        r.enabled()
      ) == 0
    ) throw new ApiException(
      HttpStatus.BAD_REQUEST,
      "MODEL_UNAVAILABLE",
      "请为启用的规则选择可用模型"
    );
    var helpers = r.helperModelIds() == null ? List.<Long>of() : r.helperModelIds();
    if (r.enabled()) {
      var ids = new ArrayList<>(helpers);
      ids.add(r.modelId());
      for (long model : ids) {
        var providers = db.queryForList(
          "select p.kind,p.base_url from ai_model m join ai_provider p on p.id=m.provider_id where m.id=?",
          model
        );
        if (providers.isEmpty()) throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "MODEL_UNAVAILABLE",
          "模型不存在"
        );
        CompositionService.requireApprovedProvider(providers.getFirst());
      }
    }
    if (
      new HashSet<>(helpers).size() != helpers.size() || helpers.contains(r.modelId())
    ) throw new ApiException(
      HttpStatus.BAD_REQUEST,
      "INVALID_COMPOSITION",
      "辅助模型不能重复或与主模型相同"
    );
    for (long helper : helpers)
      if (
        db.queryForObject(
          "select count(*) from ai_model m join ai_provider p on p.id=m.provider_id where m.id=? and m.model_type in ('CHAT','TEXT') and (?=false or (m.enabled and p.enabled))",
          Integer.class,
          helper,
          r.enabled()
        ) == 0
      ) throw new ApiException(
        HttpStatus.BAD_REQUEST,
        "INVALID_COMPOSITION",
        "辅助模型必须为可用文字或聊天模型"
      );
    if (id == null) db.update(
      "insert into model_route_rule(name,user_tier,min_stars,max_stars,model_id,priority,enabled,helper_model_ids,min_characters) values (?,?,?,?,?,?,?,cast(? as bigint[]),?)",
      r.name(),
      r.userTier(),
      r.minStars(),
      r.maxStars(),
      r.modelId(),
      r.priority(),
      r.enabled(),
      array(helpers),
      r.minCharacters()
    );
    else if (
      db.update(
        "update model_route_rule set name=?,user_tier=?,min_stars=?,max_stars=?,model_id=?,priority=?,enabled=?,helper_model_ids=cast(? as bigint[]),min_characters=? where id=?",
        r.name(),
        r.userTier(),
        r.minStars(),
        r.maxStars(),
        r.modelId(),
        r.priority(),
        r.enabled(),
        array(helpers),
        r.minCharacters(),
        id
      ) != 1
    ) throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "路由规则不存在");
    audit("ROUTE_UPDATE", r.userTier() + " model:" + r.modelId());
  }

  @PutMapping("/users/{id}/tier")
  @Transactional
  public void tier(@PathVariable long id, @Valid @RequestBody RouteAdminTierDTO r) {
    current.admin();
    if (
      db.update(
        "update app_user set membership_tier=? where id=? and role<>'GUEST'",
        r.tier(),
        id
      ) != 1
    ) throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "用户不存在");
    audit("USER_TIER_UPDATE", "user:" + id + " tier:" + r.tier());
  }

  @GetMapping("/characters/all")
  public Object cards() {
    current.admin();
    return db.queryForList(
      "select id,name,category,status,visibility,stars_per_message,offline_reason,offlined_at from character_card where deleted_at is null order by id desc limit 200"
    );
  }

  @PutMapping("/characters/{id}/price")
  @Transactional
  public void price(@PathVariable long id, @Valid @RequestBody RouteAdminPriceDTO r) {
    current.admin();
    if (
      db.update(
        "update character_card set stars_per_message=?,updated_at=now() where id=? and deleted_at is null",
        r.stars(),
        id
      ) != 1
    ) throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "角色不存在");
    audit("CHARACTER_PRICE_UPDATE", "character:" + id + " stars:" + r.stars());
  }

  private String array(List<Long> ids) {
    return "{" + String.join(",", ids.stream().map(String::valueOf).toList()) + "}";
  }

  private void audit(String action, String subject) {
    db.update(
      "insert into audit_log(actor_id,action,subject) values (?,?,?)",
      current.id(),
      action,
      subject
    );
  }
}
