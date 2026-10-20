package com.aivora.system.controller;

import com.aivora.common.*;
import com.aivora.system.dto.*;
import com.aivora.system.service.ModelAdminService;
import com.aivora.wallet.service.WalletService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

  private final JdbcTemplate db;
  private final CurrentUser current;
  private final WalletService wallet;
  private final ModelAdminService models;

  public AdminController(
    JdbcTemplate db,
    CurrentUser current,
    WalletService wallet,
    ModelAdminService models
  ) {
    this.db = db;
    this.current = current;
    this.wallet = wallet;
    this.models = models;
  }

  @GetMapping("/overview")
  public Object overview() {
    current.admin();
    return db.queryForMap(
      "select (select count(*) from app_user where role<>'GUEST') as users, (select count(*) from character_card where status='PUBLISHED' and visibility='PUBLIC' and deleted_at is null) as published, (select count(*) from character_card where status='REVIEW' and deleted_at is null) as pending"
    );
  }

  @GetMapping("/orders")
  public Object orders() {
    current.admin();
    return db.queryForList(
      "select o.*,u.nickname from recharge_order o join app_user u on u.id=o.user_id order by o.created_at desc limit 200"
    );
  }

  @GetMapping("/ledger")
  public Object ledger() {
    current.admin();
    return db.queryForList(
      "select l.*,u.nickname from wallet_ledger l join app_user u on u.id=l.user_id order by l.id desc limit 200"
    );
  }

  @GetMapping("/reconciliation")
  public Object reconciliation() {
    current.admin();
    return db.queryForList(
      "select r.id,r.request_key,r.user_id,r.status,r.cost_stars,r.created_at from chat_request r where (r.status='STARTED' and r.created_at<now()-interval '5 minutes') or (r.status='FAILED' and r.cost_stars>0 and not exists(select 1 from wallet_ledger l where l.reference_key='refund:'||r.request_key)) order by r.id desc limit 200"
    );
  }

  @GetMapping("/status")
  public Object status() {
    current.admin();
    return db.queryForMap(
      "select now() as checked_at, (select count(*) from ai_provider where enabled) as enabled_providers,(select count(*) from ai_model m join ai_provider p on p.id=m.provider_id where m.enabled and p.enabled) as enabled_models,(select count(*) from chat_request where status='STARTED') as running_requests"
    );
  }

  @GetMapping("/status/errors")
  public Object errors() {
    current.admin();
    return db.queryForList(
      "select r.id,r.request_key,r.error_message,r.cost_stars,r.created_at,m.model_name,p.name as provider,exists(select 1 from wallet_ledger l where l.reference_key='refund:'||r.request_key) as refunded from chat_request r left join ai_model m on m.id=r.model_id left join ai_provider p on p.id=m.provider_id where r.status='FAILED' order by r.id desc limit 50"
    );
  }

  @GetMapping("/characters/review")
  public Object queue() {
    current.admin();
    return db.queryForList(
      "select c.id,c.owner_id,c.name,c.summary,c.persona,c.system_prompt,c.opening,c.tags,c.category,c.avatar_url,c.stars_per_message,c.created_at,u.nickname as owner_name from character_card c join app_user u on u.id=c.owner_id where c.status='REVIEW' and c.deleted_at is null order by c.created_at"
    );
  }

  @PostMapping("/characters/{id}/approve")
  @Transactional
  public void approve(@PathVariable long id) {
    transition(id, "PUBLISHED", "");
  }

  @PostMapping("/characters/{id}/reject")
  @Transactional
  public void reject(@PathVariable long id, @Valid @RequestBody AdminReviewDTO review) {
    if (review.reason() == null || review.reason().isBlank()) throw new ApiException(
      HttpStatus.BAD_REQUEST,
      "REASON_REQUIRED",
      "请填写驳回原因"
    );
    transition(id, "DRAFT", review.reason().trim());
  }

  @PostMapping("/characters/{id}/offline")
  @Transactional
  public void offline(@PathVariable long id, @Valid @RequestBody AdminReviewDTO review) {
    current.admin();
    String reason = review.reason() == null ? "" : review.reason().trim();
    if (reason.isEmpty()) throw new ApiException(
      HttpStatus.BAD_REQUEST,
      "REASON_REQUIRED",
      "请填写停用下架原因"
    );
    if (
      db.update(
        "update character_card set status='OFFLINE',visibility='PRIVATE',review_note=?,offline_reason=?,offlined_at=now(),offlined_by=?,updated_at=now() where id=? and status='PUBLISHED' and deleted_at is null",
        reason,
        reason,
        current.id(),
        id
      ) != 1
    ) throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE", "仅可下架已发布的角色卡");
    db.update(
      "insert into audit_log(actor_id,action,subject) values (?,?,?)",
      current.id(),
      "CHARACTER_OFFLINE",
      "character:" + id + " reason:" + reason.substring(0, Math.min(100, reason.length()))
    );
  }

  private void transition(long id, String status, String reason) {
    current.admin();
    if (
      db.update(
        "update character_card set status=?,visibility=?,review_note=?,updated_at=now() where id=? and status='REVIEW' and deleted_at is null",
        status,
        status.equals("PUBLISHED") ? "PUBLIC" : "PRIVATE",
        reason,
        id
      ) != 1
    ) throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE", "角色卡不在待审核状态");
    db.update(
      "insert into audit_log(actor_id,action,subject) values (?,?,?)",
      current.id(),
      "CHARACTER_" + status,
      "character:" + id + " reason:" + reason.substring(0, Math.min(100, reason.length()))
    );
  }

  @PostMapping("/providers")
  public Map<String, Long> provider(@Valid @RequestBody AdminProviderDTO req) {
    current.admin();
    return Map.of(
      "id",
      models.createProvider(
        req.name(),
        req.kind(),
        req.baseUrl(),
        req.apiKey(),
        req.enabled(),
        current.id()
      )
    );
  }

  @PostMapping("/models")
  public Map<String, Long> model(@Valid @RequestBody AdminModelDTO req) {
    current.admin();
    Long id = db.queryForObject(
      "insert into ai_model(provider_id,model_name,star_per_request,enabled) values (?,?,?,true) returning id",
      Long.class,
      req.providerId(),
      req.modelName(),
      req.starPerRequest()
    );
    db.update(
      "insert into audit_log(actor_id,action,subject) values (?,?,?)",
      current.id(),
      "MODEL_CREATE",
      "model:" + id
    );
    return Map.of("id", id);
  }

  @GetMapping("/users")
  public Object users() {
    current.admin();
    return db.queryForList(
      "select u.id,u.email,u.nickname,u.role,u.membership_tier,u.status,w.balance from app_user u join wallet w on w.user_id=u.id where u.role<>'GUEST' order by u.id desc limit 100"
    );
  }

  @GetMapping("/audit")
  public Object audit() {
    current.admin();
    return db.queryForList("select * from audit_log order by id desc limit 100");
  }

  @Transactional
  @PostMapping("/wallet/adjust")
  public Map<String, Long> adjust(@Valid @RequestBody AdminAdjustmentDTO req) {
    current.admin();
    if (req.delta() == 0) throw new ApiException(
      HttpStatus.BAD_REQUEST,
      "INVALID_AMOUNT",
      "调整数量不能为零"
    );
    long balance = wallet.change(
      req.userId(),
      req.delta(),
      "ADMIN_ADJUST:" + req.reason(),
      "admin:" + req.referenceKey()
    );
    db.update(
      "insert into audit_log(actor_id,action,subject) values (?,?,?)",
      current.id(),
      "WALLET_ADJUST",
      "user:" + req.userId() + " delta:" + req.delta()
    );
    return Map.of("balance", balance);
  }

  @PostMapping("/recharge/packages")
  public Map<String, Long> rechargePackage(@Valid @RequestBody AdminPackageDTO req) {
    current.admin();
    Long id = db.queryForObject(
      "insert into recharge_package(name,amount_cents,stars) values (?,?,?) returning id",
      Long.class,
      req.name(),
      req.amountCents(),
      req.stars()
    );
    db.update(
      "insert into audit_log(actor_id,action,subject) values (?,?,?)",
      current.id(),
      "PACKAGE_CREATE",
      "package:" + id
    );
    return Map.of("id", id);
  }
}
