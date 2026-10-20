package com.aivora.chat.service;

import com.aivora.common.ApiException;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class RoutingService {

  private final JdbcTemplate db;

  public RoutingService(JdbcTemplate db) {
    this.db = db;
  }

  public record Selection(
    long modelId,
    long stars,
    String tier,
    Map<String, Object> character,
    Map<String, Object> model
  ) {
    public List<Long> helperIds() {
      Object value = model.get("helper_model_ids");
      if (value == null) return List.of();
      try {
        return Arrays.asList((Long[]) ((java.sql.Array) value).getArray());
      } catch (java.sql.SQLException e) {
        throw new IllegalStateException(e);
      }
    }

    public int minCharacters() {
      return ((Number) model.getOrDefault("min_characters", 0)).intValue();
    }
  }

  public Selection resolve(long user, long character) {
    var cards = db.queryForList(
      "select c.*,u.role,u.membership_tier from character_card c cross join app_user u where c.id=? and u.id=? and c.deleted_at is null and c.status<>'OFFLINE' and ((c.visibility='PUBLIC' and c.status='PUBLISHED') or c.owner_id=u.id)",
      character,
      user
    );
    if (cards.isEmpty()) throw new ApiException(
      HttpStatus.NOT_FOUND,
      "CHARACTER_UNAVAILABLE",
      "角色已下架、不存在或无权访问"
    );
    var card = cards.getFirst();
    long stars = ((Number) card.get("stars_per_message")).longValue();
    String tier = "GUEST".equals(card.get("role"))
      ? "GUEST"
      : String.valueOf(card.get("membership_tier"));
    var model = selectModel(tier, stars);
    return new Selection(((Number) model.get("id")).longValue(), stars, tier, card, model);
  }

  public Map<String, Object> creationModel(long user) {
    var users = db.queryForList(
      "select membership_tier from app_user where id=? and role='USER' and status='ACTIVE'",
      user
    );
    if (users.isEmpty()) throw new ApiException(
      HttpStatus.FORBIDDEN,
      "USER_REQUIRED",
      "请登录用户账号后创建角色"
    );
    return selectModel(String.valueOf(users.getFirst().get("membership_tier")), 1L);
  }

  private Map<String, Object> selectModel(String tier, long stars) {
    var models = db.queryForList(
      "select m.id,m.model_name,p.kind,p.base_url,p.api_key_ciphertext,r.helper_model_ids,r.min_characters from model_route_rule r join ai_model m on m.id=r.model_id join ai_provider p on p.id=m.provider_id where r.enabled and m.enabled and p.enabled and p.kind='OPENAI_COMPATIBLE' and p.api_key_ciphertext is not null and p.base_url like 'https://dashscope.aliyuncs.com/%' and m.model_type in ('CHAT','TEXT') and not exists(select 1 from unnest(r.helper_model_ids) h(id) left join ai_model hm on hm.id=h.id left join ai_provider hp on hp.id=hm.provider_id where hm.id is null or not hm.enabled or not hp.enabled or hm.model_type not in ('CHAT','TEXT')) and r.user_tier=? and r.min_stars<=? and (r.max_stars is null or r.max_stars>=?) order by r.priority desc,r.min_stars desc,coalesce(r.max_stars,2147483647)-r.min_stars asc,r.id desc limit 1",
      tier,
      stars,
      stars
    );
    if (models.isEmpty()) models = db.queryForList(
      "select m.id,m.model_name,p.kind,p.base_url,p.api_key_ciphertext from ai_model m join ai_provider p on p.id=m.provider_id where m.is_default and m.enabled and p.enabled and p.kind='OPENAI_COMPATIBLE' and p.api_key_ciphertext is not null and p.base_url like 'https://dashscope.aliyuncs.com/%' and m.model_type in ('CHAT','TEXT') limit 1"
    );
    if (models.isEmpty()) models = db.queryForList(
      "select m.id,m.model_name,p.kind,p.base_url,p.api_key_ciphertext from ai_model m join ai_provider p on p.id=m.provider_id where m.enabled and p.enabled and p.kind='OPENAI_COMPATIBLE' and p.api_key_ciphertext is not null and p.base_url like 'https://dashscope.aliyuncs.com/%' and m.model_type in ('CHAT','TEXT') order by m.id limit 1"
    );
    if (models.isEmpty()) throw new ApiException(
      HttpStatus.SERVICE_UNAVAILABLE,
      "NO_AVAILABLE_ROUTE",
      "暂时没有可用的聊天服务，请稍后再试"
    );
    return models.getFirst();
  }
}
