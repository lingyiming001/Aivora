package com.aivora.character.controller;

import com.aivora.character.dto.*;
import com.aivora.common.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/characters")
public class EngagementController {

  private final JdbcTemplate db;
  private final CurrentUser current;

  public EngagementController(JdbcTemplate db, CurrentUser current) {
    this.db = db;
    this.current = current;
  }

  @GetMapping("/engagements")
  public Object all() {
    long user = current.optionalId();
    return db.queryForList(
      "select c.id as character_id,count(*) filter(where e.favorite) as favorites,count(*) filter(where e.vote=1) as likes,count(*) filter(where e.vote=-1) as dislikes,coalesce(bool_or(e.favorite) filter(where e.user_id=?),false) as favorite,coalesce(max(e.vote) filter(where e.user_id=?),0) as vote from character_card c left join character_engagement e on e.character_id=c.id where c.deleted_at is null and ((c.visibility='PUBLIC' and c.status='PUBLISHED') or c.owner_id=?) group by c.id",
      user,
      user,
      user
    );
  }

  @GetMapping("/favorites")
  public Object favorites() {
    long user = current.id();
    return db.queryForList(
      "select c.id,c.name,c.summary,c.avatar_url,c.stars_per_message from character_engagement e join character_card c on c.id=e.character_id where e.user_id=? and e.favorite and c.deleted_at is null and ((c.visibility='PUBLIC' and c.status='PUBLISHED') or c.owner_id=?) order by e.updated_at desc",
      user,
      user
    );
  }

  @Transactional
  @PutMapping("/{id}/engagement")
  public void change(@PathVariable long id, @Valid @RequestBody EngagementChangeDTO change) {
    long user = current.id();
    if (
      !"USER".equals(
        db.queryForObject(
          "select role from app_user where id=? and status='ACTIVE'",
          String.class,
          user
        )
      )
    ) throw new ApiException(HttpStatus.FORBIDDEN, "USER_REQUIRED", "请登录用户账号");
    if (
      db
        .queryForList(
          "select id from character_card where id=? and deleted_at is null and ((visibility='PUBLIC' and status='PUBLISHED') or owner_id=?)",
          id,
          user
        )
        .isEmpty()
    ) throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "角色不可用");
    if (change.favorite() == null && change.vote() == null) throw new ApiException(
      HttpStatus.BAD_REQUEST,
      "EMPTY_CHANGE",
      "请选择收藏或评价"
    );
    db.update(
      "insert into character_engagement(user_id,character_id) values (?,?) on conflict do nothing",
      user,
      id
    );
    db.update(
      "update character_engagement set favorite=coalesce(?,favorite),vote=coalesce(?,vote),updated_at=now() where user_id=? and character_id=?",
      change.favorite(),
      change.vote(),
      user,
      id
    );
  }
}
