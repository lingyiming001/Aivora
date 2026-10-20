package com.aivora.chat.service;

import static org.junit.jupiter.api.Assertions.*;

import com.aivora.ai.domain.AiGateway;
import java.util.*;
import org.junit.jupiter.api.Test;

class ChatPromptBuilderTest {

  private final Map<String, Object> card = Map.of(
    "name",
    "小满",
    "summary",
    "日常伙伴",
    "persona",
    "耐心倾听",
    "system_prompt",
    "简洁自然",
    "opening",
    "今天过得怎样？"
  );

  private AiGateway.Message message(String role, String text) {
    return new AiGateway.Message(role, text);
  }

  @Test
  void retainsChronologyLatestRequestAndSeparateUserPreferences() {
    var history = ChatPromptBuilder.build(
      card,
      "用户偏好：简短",
      List.of(
        message("user", "今天请详细分析"),
        message("assistant", "你上次说是周一"),
        message("user", "面试是周一")
      )
    );
    assertEquals("system", history.getFirst().role());
    assertTrue(history.getFirst().content().contains("小满"));
    assertTrue(history.getFirst().content().contains("耐心倾听"));
    assertFalse(history.getFirst().content().contains("今天过得怎样？"));
    assertEquals(
      List.of(
        message("user", "用户偏好：简短"),
        message("user", "面试是周一"),
        message("assistant", "你上次说是周一"),
        message("user", "今天请详细分析")
      ),
      history
        .stream()
        .filter(m -> !"system".equals(m.role()))
        .toList()
    );
  }

  @Test
  void doesNotPresentOldAssistantReplyWithoutItsUserTurn() {
    var history = ChatPromptBuilder.build(
      card,
      "",
      List.of(message("user", "今天想庆祝"), message("assistant", "也许你很难过"))
    );
    assertEquals(
      1,
      history
        .stream()
        .filter(m -> !"system".equals(m.role()))
        .count()
    );
    assertEquals("今天想庆祝", history.getLast().content());
  }

  @Test
  void boundsContextByKeepingCompleteRecentTurns() {
    var history = ChatPromptBuilder.build(
      card,
      "",
      List.of(
        message("user", "最新问题"),
        message("assistant", "近期回答"),
        message("user", "近期事件"),
        message("assistant", "x".repeat(ChatPromptBuilder.MAX_HISTORY_CHARACTERS))
      )
    );
    assertEquals(
      List.of("近期事件", "近期回答", "最新问题"),
      history
        .stream()
        .filter(m -> !"system".equals(m.role()))
        .toList()
        .stream()
        .map(AiGateway.Message::content)
        .toList()
    );
  }

  @Test
  void boundsOversizedNewestMessageWithoutDiscardingIt() {
    var history = ChatPromptBuilder.build(
      card,
      null,
      List.of(message("user", "x".repeat(ChatPromptBuilder.MAX_HISTORY_CHARACTERS + 1000)))
    );
    assertEquals(
      1,
      history
        .stream()
        .filter(m -> !"system".equals(m.role()))
        .count()
    );
    assertEquals(ChatPromptBuilder.MAX_HISTORY_CHARACTERS, history.getLast().content().length());
  }

  @Test
  void doesNotMixCharactersOrMutateInput() {
    var input = List.of(message("user", "你好"));
    var a = ChatPromptBuilder.build(card, null, input);
    var b = ChatPromptBuilder.build(
      Map.of("name", "陈屿", "persona", "咖啡电影爱好者"),
      null,
      input
    );
    assertTrue(b.getFirst().content().contains("陈屿"));
    assertFalse(b.getFirst().content().contains("小满"));
    assertFalse(a.getFirst().content().contains("陈屿"));
    assertEquals(List.of(message("user", "你好")), input);
  }

  @Test
  void keepsGreetingGuidanceSeparateFromActualTasksAndDoesNotInjectOpening() {
    var role = new HashMap<>(card);
    role.put("opening", "SECRET_OPENING_STAGE");
    var greeting = ChatPromptBuilder.build(role, "", List.of(message("user", "你好呀！")));
    assertFalse(greeting.stream().anyMatch(m -> m.content().contains("SECRET_OPENING_STAGE")));
    assertNotEquals(
      ChatPromptBuilder.turnGuidance("你好"),
      ChatPromptBuilder.turnGuidance("你好，帮我写一封信")
    );
    assertEquals(ChatPromptBuilder.turnGuidance("你好"), ChatPromptBuilder.turnGuidance("在吗？"));
    assertNotEquals(
      ChatPromptBuilder.turnGuidance("你好"),
      ChatPromptBuilder.turnGuidance("你好，帮我写一封信")
    );
    assertEquals("你好呀！", greeting.getLast().content());
  }

  @Test
  void limitsLiteraryStyleToRequestedScenesAndKeepsPlainCharacterDirections() {
    var literary = new HashMap<>(card);
    literary.put("system_prompt", "善用具象比喻SCENE_STYLE_SENTINEL，所有表达扎根于可触摸的细节");
    var casual = ChatPromptBuilder.build(literary, "", List.of(message("user", "面吃多了，撑")));
    assertFalse(casual.stream().anyMatch(m -> m.content().contains("SCENE_STYLE_SENTINEL")));
    var scene = ChatPromptBuilder.build(
      literary,
      "",
      List.of(message("user", "来演一段我到书店避雨的剧情"))
    );
    assertTrue(scene.stream().anyMatch(m -> m.content().contains("SCENE_STYLE_SENTINEL")));
    assertFalse(ChatPromptBuilder.creativeRequest("别写剧情，正常说话"));
    assertTrue(
      ChatPromptBuilder.build(card, "", List.of(message("user", "聊聊书")))
        .stream()
        .anyMatch(m -> m.content().contains("简洁自然"))
    );
  }

  @Test
  void adjustsRhythmOnlyFromPreviousAssistantReplies() {
    assertEquals(
      "",
      ChatPromptBuilder.rhythm(
        List.of(
          message("user", "怎么了？"),
          message("assistant", "没事。"),
          message("user", "为什么？")
        )
      )
    );
    var interview = List.of(
      message("user", "吃面了"),
      message("assistant", "什么面？"),
      message("user", "鸡蛋面"),
      message("assistant", "自己做的？"),
      message("user", "对啊")
    );
    assertFalse(ChatPromptBuilder.rhythm(interview).isBlank());
    assertEquals("", ChatPromptBuilder.rhythm(List.of(message("assistant", "什么面？"))));
    var repeats = List.of(
      message("assistant", "哈哈，吃饱了。"),
      message("user", "嗯"),
      message("assistant", "哈哈，挺好。"),
      message("user", "对")
    );
    assertFalse(ChatPromptBuilder.rhythm(repeats).isBlank());
    assertEquals(
      "",
      ChatPromptBuilder.rhythm(
        List.of(
          message("assistant", "哈哈，吃饱了。"),
          message("user", "嗯"),
          message("assistant", "那就歇会儿。"),
          message("user", "对")
        )
      )
    );
  }

  @Test
  void addsContinuityReminderOnlyForActualMultiTurnUserHistory() {
    assertEquals(
      "",
      ChatPromptBuilder.continuity(
        List.of(message("system", "设定"), message("assistant", "你好"), message("user", "嗨"))
      )
    );
    assertFalse(
      ChatPromptBuilder.continuity(
        List.of(
          message("user", "盐放多了"),
          message("assistant", "嗯"),
          message("user", "我妈也这么说")
        )
      ).isBlank()
    );
  }
}
