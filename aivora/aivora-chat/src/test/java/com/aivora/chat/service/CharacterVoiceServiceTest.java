package com.aivora.chat.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class CharacterVoiceServiceTest {

  Map<String, Object> role() {
    return new HashMap<>(
      Map.of(
        "name",
        "林屿",
        "persona",
        "一位店主。".repeat(100),
        "system_prompt",
        "善用比喻和具象细节"
      )
    );
  }

  @Test
  void derivesOnceAndInvalidatesWhenAuthorChangesRole() {
    var models = mock(CompositionService.class);
    doAnswer(call -> {
      Consumer<String> out = call.getArgument(3);
      out.accept(
        "{\"facts\":\"海边的书店店主，喜欢旅行和地图。\",\"voice\":\"温和直接，说话简短。\"}"
      );
      return null;
    })
      .when(models)
      .stream(anyMap(), anyList(), anyList(), any());
    var service = new CharacterVoiceService(models, new ObjectMapper());
    var card = role();
    card.put("summary", "LITERARY_SUMMARY_SENTINEL");
    assertEquals(
      "海边的书店店主，喜欢旅行和地图。",
      service.forTurn(card, Map.of(), "你好").get("persona")
    );
    assertEquals("", service.forTurn(card, Map.of(), "吃饱了").get("summary"));
    verify(models, times(1)).stream(anyMap(), anyList(), anyList(), any());
    card.put("persona", card.get("persona") + "现在喜欢电影。");
    service.forTurn(card, Map.of(), "聊聊天");
    verify(models, times(2)).stream(anyMap(), anyList(), anyList(), any());
    assertTrue(card.get("persona").toString().contains("现在喜欢电影"));
  }

  @Test
  void creativeSceneAndSimpleRolesUseOriginalWithoutExtraCall() {
    var models = mock(CompositionService.class);
    var service = new CharacterVoiceService(models, new ObjectMapper());
    var card = role();
    assertSame(card, service.forTurn(card, Map.of(), "来演一段书店避雨的剧情"));
    var simple = Map.<String, Object>of("persona", "热爱电影的朋友", "system_prompt", "轻松幽默");
    assertSame(simple, service.forTurn(simple, Map.of(), "你好"));
    verifyNoInteractions(models);
  }

  @Test
  void invalidOrFailedProfileDoesNotLeakStageDirectionsOrFailChat() {
    var models = mock(CompositionService.class);
    doThrow(new IllegalStateException("unavailable"))
      .when(models)
      .stream(anyMap(), anyList(), anyList(), any());
    var card = role();
    var profile = new CharacterVoiceService(models, new ObjectMapper()).forTurn(
      card,
      Map.of(),
      "你好"
    );
    assertEquals("", profile.get("persona"));
    assertEquals("林屿", profile.get("name"));
    assertNotEquals(card.get("system_prompt"), profile.get("system_prompt"));
  }
}
