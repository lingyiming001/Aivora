package com.aivora.chat.service;

import com.aivora.common.ApiException;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BatchChatService {

  private final JdbcTemplate db;

  public BatchChatService(JdbcTemplate db) {
    this.db = db;
  }

  @Transactional
  public int delete(long user, List<Long> requested) {
    var ids = requested.stream().distinct().sorted().toList();
    for (long id : ids)
      if (
        db
          .queryForList(
            "select id from chat_session where id=? and user_id=? and deleted_at is null for update",
            id,
            user
          )
          .isEmpty()
      ) throw new ApiException(
        HttpStatus.NOT_FOUND,
        "NOT_FOUND",
        "部分对话不存在或不属于当前用户，请刷新后重试"
      );
    for (long id : ids)
      if (
        db.queryForObject(
          "select count(*) from chat_request where session_id=? and status='STARTED'",
          Integer.class,
          id
        ) > 0
      ) throw new ApiException(
        HttpStatus.CONFLICT,
        "CHAT_BUSY",
        "所选对话中有角色正在回复，请稍后再删除"
      );
    for (long id : ids)
      db.update("update chat_session set deleted_at=now() where id=? and user_id=?", id, user);
    return ids.size();
  }
}
