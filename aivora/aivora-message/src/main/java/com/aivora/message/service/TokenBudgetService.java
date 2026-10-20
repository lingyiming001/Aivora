package com.aivora.message.service;

import com.aivora.ai.domain.ModelUsageEvent;
import com.aivora.common.ApiException;
import java.time.*;
import java.util.*;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@EnableScheduling
public class TokenBudgetService {

  private final JdbcTemplate db;
  private final TransactionTemplate tx;

  public TokenBudgetService(JdbcTemplate db, PlatformTransactionManager manager) {
    this.db = db;
    tx = new TransactionTemplate(manager);
  }

  public static String month() {
    return YearMonth.now(ZoneId.of("Asia/Shanghai")).toString();
  }

  public static double remainingPercent(long limit, long used) {
    return limit <= 0 ? 0 : Math.max(0, ((limit - (double) used) * 100) / limit);
  }

  @EventListener
  public void record(ModelUsageEvent event) {
    try {
      tx.executeWithoutResult(s -> {
        db.update(
          "insert into model_usage_record(id,model_name,kind,total_tokens,succeeded) values (?,?,?,?,?) on conflict do nothing",
          event.id(),
          event.model(),
          event.kind(),
          event.tokens(),
          event.succeeded()
        );
        assess();
      });
    } catch (Exception e) {
      LoggerFactory.getLogger(getClass()).warn("Token usage recording unavailable", e);
    }
  }

  @Scheduled(fixedDelay = 60000, initialDelay = 10000)
  public void monitor() {
    try {
      tx.executeWithoutResult(s -> assess());
    } catch (Exception e) {
      LoggerFactory.getLogger(getClass()).warn("Token budget monitor unavailable", e);
    }
  }

  private Map<String, Object> assess() {
    String month = month();
    var policy = db.queryForMap("select * from token_budget_policy where id=1 for update");
    db.update("insert into token_budget_month(month) values (?) on conflict do nothing", month);
    var period = db.queryForMap("select * from token_budget_month where month=? for update", month);
    var usage = db.queryForMap(
      "select coalesce(sum(total_tokens),0) as tokens,count(*) as calls,count(*) filter(where total_tokens is null) as unknown from model_usage_record where created_at >= cast(?||'-01' as date)::timestamp at time zone 'Asia/Shanghai' and created_at < (cast(?||'-01' as date)+interval '1 month')::timestamp at time zone 'Asia/Shanghai'",
      month,
      month
    );
    long limit = ((Number) policy.get("token_limit")).longValue(),
      baseline = ((Number) period.get("baseline_tokens")).longValue(),
      measured = ((Number) usage.get("tokens")).longValue(),
      used = Math.addExact(baseline, measured);
    int threshold = ((Number) policy.get("warning_percent")).intValue();
    double remaining = remainingPercent(limit, used);
    boolean low = Boolean.TRUE.equals(policy.get("enabled")) && limit > 0 && remaining <= threshold;
    if (low && !Boolean.TRUE.equals(period.get("alerted"))) {
      db.update(
        "insert into platform_notice(month,title,body) values (?,?,?)",
        month,
        "月度Token预算余量不足",
        String.format(
          Locale.ROOT,
          "%s月度预算剩余%.1f%%，已用%,d / 预算%,d Token。请检查供应商余额并充值，或调整平台月度预算。",
          month,
          remaining,
          used,
          limit
        )
      );
      db.update("update token_budget_month set alerted=true where month=?", month);
    } else if (!low && Boolean.TRUE.equals(period.get("alerted"))) db.update(
      "update token_budget_month set alerted=false where month=?",
      month
    );
    var result = new LinkedHashMap<String, Object>();
    result.put("month", month);
    result.put("limit", limit);
    result.put("threshold", threshold);
    result.put("enabled", policy.get("enabled"));
    result.put("baseline", baseline);
    result.put("measured", measured);
    result.put("used", used);
    result.put("remaining", Math.max(0, limit - used));
    result.put("remainingPercent", remaining);
    result.put("calls", usage.get("calls"));
    result.put("unknownCalls", usage.get("unknown"));
    result.put("low", low);
    return result;
  }

  public Object status() {
    return tx.execute(s -> assess());
  }

  public void configure(
    String month,
    long limit,
    int threshold,
    long baseline,
    boolean enabled,
    long actor
  ) {
    if (
      !month().equals(month) ||
      limit < 1 ||
      limit > 1000000000000L ||
      baseline < 0 ||
      baseline > 1000000000000L ||
      threshold < 1 ||
      threshold > 99
    ) throw new ApiException(
      HttpStatus.BAD_REQUEST,
      "INVALID_BUDGET",
      "预算或阈值无效，请刷新后重试"
    );
    tx.executeWithoutResult(s -> {
      db.queryForMap("select id from token_budget_policy where id=1 for update");
      db.update(
        "update token_budget_policy set token_limit=?,warning_percent=?,enabled=?,updated_at=now() where id=1",
        limit,
        threshold,
        enabled
      );
      db.update(
        "insert into token_budget_month(month,baseline_tokens) values (?,?) on conflict(month) do update set baseline_tokens=excluded.baseline_tokens",
        month,
        baseline
      );
      db.update(
        "insert into audit_log(actor_id,action,subject) values (?,'TOKEN_BUDGET_CONFIG',?)",
        actor,
        "month=" + month + " limit=" + limit + " threshold=" + threshold
      );
      assess();
    });
  }

  public Object notices(long user) {
    return db.queryForList(
      "select n.*,exists(select 1 from platform_notice_read r where r.notice_id=n.id and r.user_id=?) as read from platform_notice n order by n.id desc limit 50",
      user
    );
  }

  public void read(long user, long id) {
    if (
      db.queryForObject("select count(*) from platform_notice where id=?", Integer.class, id) == 0
    ) throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "通知不存在");
    db.update(
      "insert into platform_notice_read(notice_id,user_id) values (?,?) on conflict do nothing",
      id,
      user
    );
  }
}
