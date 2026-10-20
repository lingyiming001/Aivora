package com.aivora.character.controller;

import com.aivora.character.dto.*;
import com.aivora.common.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/characters")
public class CharacterController {

  private final JdbcTemplate db;
  private final CurrentUser current;

  public CharacterController(JdbcTemplate db, CurrentUser current) {
    this.db = db;
    this.current = current;
  }

  @GetMapping
  public Object square(
    @RequestParam(defaultValue = "") String q,
    @RequestParam(defaultValue = "") String category
  ) {
    return db.queryForList(
      "select id,name,summary,category,tags,avatar_url,stars_per_message,created_at from character_card where status='PUBLISHED' and visibility='PUBLIC' and deleted_at is null and (name ilike ? or summary ilike ?) and (?='' or category=?) order by created_at desc limit 100",
      "%" + q + "%",
      "%" + q + "%",
      category,
      category
    );
  }

  @GetMapping("/{id}")
  public Object detail(@PathVariable long id) {
    var cards = db.queryForList(
      "select id,name,summary,opening,category,tags,avatar_url,stars_per_message,visibility,status from character_card where id=? and deleted_at is null and ((status='PUBLISHED' and visibility='PUBLIC') or owner_id=?)",
      id,
      current.optionalId()
    );
    if (cards.isEmpty()) throw notFound();
    return cards.getFirst();
  }

  @GetMapping("/{id}/edit")
  public Object edit(@PathVariable long id) {
    var rows = db.queryForList(
      "select id,name,summary,persona,system_prompt,opening,category,tags,avatar_url,stars_per_message,visibility,status from character_card where id=? and owner_id=? and deleted_at is null",
      id,
      current.id()
    );
    if (rows.isEmpty()) throw notFound();
    return rows.getFirst();
  }

  @GetMapping("/mine")
  public Object mine() {
    return db.queryForList(
      "select id,name,summary,category,tags,status,review_note,visibility,stars_per_message,avatar_url,created_at from character_card where owner_id=? and deleted_at is null order by id desc",
      current.id()
    );
  }

  @PostMapping
  public Object create(@Valid @RequestBody CharacterCardDTO c) {
    Long id = db.queryForObject(
      "insert into character_card(owner_id,name,summary,persona,system_prompt,opening,avatar_url,category,tags,visibility) values (?,?,?,?,?,?,?,?,?,'PRIVATE') returning id",
      Long.class,
      current.id(),
      c.name(),
      value(c.summary()),
      value(c.persona()),
      value(c.systemPrompt()),
      value(c.opening()),
      avatar(c.avatarUrl()),
      category(c.category()),
      value(c.tags())
    );
    return Map.of("id", id, "status", "DRAFT", "visibility", "PRIVATE");
  }

  @PutMapping("/{id}")
  public void update(@PathVariable long id, @Valid @RequestBody CharacterCardDTO c) {
    if (
      db.update(
        "update character_card set name=?,summary=?,persona=?,system_prompt=?,opening=?,avatar_url=?,category=?,tags=?,updated_at=now() where id=? and owner_id=? and visibility='PRIVATE' and deleted_at is null",
        c.name(),
        value(c.summary()),
        value(c.persona()),
        value(c.systemPrompt()),
        value(c.opening()),
        avatar(c.avatarUrl()),
        category(c.category()),
        value(c.tags()),
        id,
        current.id()
      ) != 1
    ) throw new ApiException(HttpStatus.CONFLICT, "NOT_EDITABLE", "仅可编辑自己的私密角色");
  }

  @DeleteMapping("/{id}")
  public void delete(@PathVariable long id) {
    if (
      db.update(
        "update character_card set deleted_at=now(),updated_at=now() where id=? and owner_id=? and deleted_at is null",
        id,
        current.id()
      ) != 1
    ) throw notFound();
  }

  @PostMapping("/{id}/submit")
  public void submit(@PathVariable long id) {
    if (
      db.update(
        "update character_card set status='REVIEW',visibility='PUBLIC',review_note='',updated_at=now() where id=? and owner_id=? and status in ('DRAFT','OFFLINE') and deleted_at is null",
        id,
        current.id()
      ) != 1
    ) throw new ApiException(
      HttpStatus.CONFLICT,
      "INVALID_STATE",
      "仅可提交自己的草稿或已下架角色"
    );
  }

  private String value(String v) {
    return Objects.toString(v, "");
  }

  public static String avatar(String v) {
    if (v == null || v.isBlank()) return "/images/character-night.png";
    if (v.startsWith("data:")) return com.aivora.common.util.AvatarValidator.validateAvatar(v);
    if (v.length() > 500) throw new ApiException(
      HttpStatus.BAD_REQUEST,
      "INVALID_AVATAR",
      "图片地址过长"
    );
    if (!v.startsWith("/images/") && !v.startsWith("https://")) throw new ApiException(
      HttpStatus.BAD_REQUEST,
      "INVALID_AVATAR",
      "头像需为站内图片或 HTTPS 地址"
    );
    return v;
  }

  private String category(String v) {
    String c = v == null ? "日常陪伴" : v;
    if (
      !List.of("日常陪伴", "创意写作", "学习成长", "工作效率", "幻想世界").contains(c)
    ) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CATEGORY", "角色分类无效");
    return c;
  }

  private ApiException notFound() {
    return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "角色不存在或无权访问");
  }
}
