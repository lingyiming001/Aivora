package com.aivora.character.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivora.chat.service.*;
import com.aivora.common.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class CharacterGenerationServiceTest {

  private final RoutingService routing = mock(RoutingService.class);
  private final CompositionService ai = mock(CompositionService.class);
  private final CharacterGenerationService service = new CharacterGenerationService(
    routing,
    ai,
    new ObjectMapper()
  );

  private String valid() {
    return "{\"name\":\"林间\",\"summary\":\"旧书店店主\",\"persona\":\"温柔，喜欢旅行\",\"systemPrompt\":\"自然简洁\",\"opening\":\"欢迎，你想读什么？\",\"category\":\"日常陪伴\",\"tags\":\"旧书,旅行\"}";
  }

  @Test
  void acceptsCompleteJsonAndFencedResponse() {
    assertEquals("林间", service.parse(valid()).name());
    assertEquals("林间", service.parse("```json\n" + valid() + "\n```").name());
  }

  @Test
  void rejectsMissingWrongTypeAndExcessiveFields() {
    for (String raw : List.of(
      "{}",
      valid().replace("日常陪伴", "未知分类"),
      valid().replace("\"林间\"", "42"),
      valid().replace("旧书店店主", "x".repeat(1001))
    ))
      assertEquals(
        "INVALID_AI_DRAFT",
        assertThrows(ApiException.class, () -> service.parse(raw)).code()
      );
  }

  @Test
  void invokesConfiguredModelAndReleasesUserAfterInvalidOutput() {
    var model = Map.<String, Object>of("id", 5L);
    when(routing.creationModel(9L)).thenReturn(model);
    doAnswer(invocation -> {
      Consumer<String> delta = invocation.getArgument(3);
      delta.accept("{}");
      return null;
    })
      .when(ai)
      .stream(eq(model), eq(List.of()), anyList(), any());
    assertThrows(ApiException.class, () -> service.generate(9, "书店店主"));
    doAnswer(invocation -> {
      Consumer<String> delta = invocation.getArgument(3);
      delta.accept(valid());
      return null;
    })
      .when(ai)
      .stream(eq(model), eq(List.of()), anyList(), any());
    assertEquals("林间", service.generate(9, "书店店主").name());
  }
}
