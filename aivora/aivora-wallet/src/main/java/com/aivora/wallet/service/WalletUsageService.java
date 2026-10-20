package com.aivora.wallet.service;

import com.aivora.common.ApiException;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class WalletUsageService {

  private final JdbcTemplate db;

  public WalletUsageService(JdbcTemplate db) {
    this.db = db;
  }

  private static final String JOIN =
    " from wallet_ledger l left join chat_request r on r.request_key=substring(l.reference_key from position(':' in l.reference_key)+1) and r.user_id=l.user_id left join chat_session s on s.id=r.session_id and s.user_id=l.user_id left join character_card c on c.id=s.character_id ";

  public Object summary(long user, int days) {
    if (!List.of(7, 30, 90).contains(days)) throw new ApiException(
      HttpStatus.BAD_REQUEST,
      "INVALID_PERIOD",
      "请选择7、30或90天"
    );
    return db.queryForList(
      "select to_char(l.created_at at time zone 'Asia/Shanghai','YYYY-MM-DD') as day,coalesce(c.id,0) as character_id,coalesce(c.name,'历史角色') as character_name,count(*) filter(where l.reason='CHAT') as messages,coalesce(sum(-l.delta) filter(where l.delta<0),0) as spent,coalesce(sum(l.delta) filter(where l.delta>0),0) as refunded,-sum(l.delta) as net, count(distinct r.session_id) as sessions,max(l.created_at) as last_at" +
        JOIN +
        "where l.user_id=? and l.reason in ('CHAT','CHAT_REFUND') and l.created_at >= ((current_timestamp at time zone 'Asia/Shanghai')::date-(? - 1))::timestamp at time zone 'Asia/Shanghai' group by 1,2,3 order by day desc,last_at desc",
      user,
      days
    );
  }

  public Object details(long user, String day, long character, int page) {
    try {
      LocalDate.parse(day);
      if (character < 0 || page < 0 || page > 10000) throw new IllegalArgumentException();
    } catch (Exception e) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILTER", "明细筛选无效");
    }
    return db.queryForList(
      "select l.id,l.delta,l.balance_after,l.reason,l.created_at,r.status as request_status,r.session_id,count(*) over() as total" +
        JOIN +
        "where l.user_id=? and l.reason in ('CHAT','CHAT_REFUND') and l.created_at >= cast(? as date)::timestamp at time zone 'Asia/Shanghai' and l.created_at < (cast(? as date)+1)::timestamp at time zone 'Asia/Shanghai' and coalesce(c.id,0)=? order by l.id desc limit 50 offset ?",
      user,
      day,
      day,
      character,
      page * 50
    );
  }
}
