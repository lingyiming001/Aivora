package com.aivora.chat.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class UserMemoryServiceTest {

  @Test
  void extractsExplicitPreferencesAndUsesLatestValue() {
    assertEquals(
      Map.of("likes", "科幻", "hobbies", "摄影", "reply_style", "回答简短"),
      UserMemoryService.extract("我喜欢音乐。我喜欢科幻。我的爱好是摄影。我希望你以后回答简短")
    );
    assertEquals(Map.of("dislikes", "咖啡"), UserMemoryService.extract("请记住，我不喜欢咖啡。"));
  }

  @Test
  void ignoresQuestionsAndQuotedOrHypotheticalPreferences() {
    for (String text : new String[] {
      "你喜欢什么？",
      "他说：我喜欢科幻",
      "如果我喜欢咖啡，你会怎么回答？",
      "“我喜欢音乐”",
      "帮我记住密码123456",
      "我的手机号是12345678901",
    })
      assertTrue(UserMemoryService.extract(text).isEmpty(), text);
  }

  @Test
  void disabledMemoryIsNeitherRecordedNorIncludedInContext() {
    var db = mock(JdbcTemplate.class);
    when(
      db.queryForObject("select memory_enabled from app_user where id=?", Boolean.class, 7L)
    ).thenReturn(false);
    var service = new UserMemoryService(db);
    service.remember(7L, "我喜欢科幻");
    assertEquals("", service.context(7L));
    verify(db, never()).update(anyString(), any(Object[].class));
  }

  @Test
  void doesNotRememberSelfCriticismQuestionsOrMomentaryFeelings() {
    for (String text : List.of(
      "我讨厌自己",
      "我不喜欢我自己",
      "我喜欢你",
      "我喜欢什么？",
      "我喜欢咖啡吗?",
      "我喜欢现在这样安静一会儿",
      "我讨厌今天的一切"
    ))
      assertTrue(UserMemoryService.extract(text).isEmpty(), text);
    assertEquals(Map.of("likes", "安静的环境"), UserMemoryService.extract("我喜欢安静的环境。"));
  }
}
