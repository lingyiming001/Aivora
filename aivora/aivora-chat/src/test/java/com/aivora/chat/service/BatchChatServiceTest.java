package com.aivora.chat.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivora.common.ApiException;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class BatchChatServiceTest {

  @Test
  void locksInOrderAndDeduplicates() {
    var db = mock(JdbcTemplate.class);
    when(db.queryForList(startsWith("select id"), anyLong(), eq(7L))).thenReturn(
      List.of(Map.of("id", 1L))
    );
    when(db.queryForObject(anyString(), eq(Integer.class), anyLong())).thenReturn(0);
    assertEquals(2, new BatchChatService(db).delete(7, List.of(3L, 1L, 3L)));
    var order = inOrder(db);
    order.verify(db).queryForList(startsWith("select id"), eq(1L), eq(7L));
    order.verify(db).queryForList(startsWith("select id"), eq(3L), eq(7L));
    verify(db, times(2)).update(anyString(), anyLong(), eq(7L));
  }

  @Test
  void unauthorizedMemberPreventsAllDeletion() {
    var db = mock(JdbcTemplate.class);
    when(db.queryForList(startsWith("select id"), eq(1L), eq(7L))).thenReturn(
      List.of(Map.of("id", 1L))
    );
    assertEquals(
      "NOT_FOUND",
      assertThrows(ApiException.class, () ->
        new BatchChatService(db).delete(7, List.of(1L, 2L))
      ).code()
    );
    verify(db, never()).update(anyString(), anyLong(), anyLong());
  }

  @Test
  void activeReplyPreventsAllDeletion() {
    var db = mock(JdbcTemplate.class);
    when(db.queryForList(startsWith("select id"), anyLong(), eq(7L))).thenReturn(
      List.of(Map.of("id", 1L))
    );
    when(db.queryForObject(anyString(), eq(Integer.class), eq(1L))).thenReturn(0);
    when(db.queryForObject(anyString(), eq(Integer.class), eq(2L))).thenReturn(1);
    assertEquals(
      "CHAT_BUSY",
      assertThrows(ApiException.class, () ->
        new BatchChatService(db).delete(7, List.of(1L, 2L))
      ).code()
    );
    verify(db, never()).update(anyString(), anyLong(), anyLong());
  }
}
