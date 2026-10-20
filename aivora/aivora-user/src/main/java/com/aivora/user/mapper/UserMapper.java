package com.aivora.user.mapper;

import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserMapper {

  private final JdbcTemplate db;

  public UserMapper(JdbcTemplate db) {
    this.db = db;
  }

  public String activeRole(long id) {
    return db.queryForObject(
      "select role from app_user where id=? and status='ACTIVE'",
      String.class,
      id
    );
  }

  public Map<String, Object> profile(long id) {
    return db.queryForMap(
      "select u.id,u.nickname,u.username,u.email,u.phone,u.avatar_url,u.bio,u.membership_tier,u.created_at,w.balance,(select count(*) from character_card c where c.owner_id=u.id and c.deleted_at is null) as character_count from app_user u join wallet w on w.user_id=u.id where u.id=?",
      id
    );
  }

  public void update(long id, String nickname, String bio, String avatar) {
    db.update(
      "update app_user set nickname=?,bio=?,avatar_url=? where id=?",
      nickname,
      bio,
      avatar,
      id
    );
  }
}
