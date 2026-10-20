package com.aivora.chat.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivora.ai.domain.AiGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class ConversationReplyServiceTest {

  List<AiGateway.Message> history(String text) {
    return List.of(new AiGateway.Message("system", "角色"), new AiGateway.Message("user", text));
  }

  @Test
  void sendsNaturalReplyWithoutEditorialCall() {
    var models = mock(CompositionService.class);
    doAnswer(call -> {
      Consumer<String> out = call.getArgument(3);
      out.accept("那你这碗面够咸的。");
      return null;
    })
      .when(models)
      .stream(anyMap(), anyList(), anyList(), any(), anyLong());
    var out = new StringBuilder();
    new ConversationReplyService(models, new ObjectMapper()).stream(
      Map.of(),
      List.of(),
      history("面盐放多了"),
      out::append
    );
    assertEquals("那你这碗面够咸的。", out.toString());
    verify(models, times(1)).stream(anyMap(), anyList(), anyList(), any(), anyLong());
  }

  @Test
  void neverExposesStageDraftAndRewritesOnlyOnce() {
    var models = mock(CompositionService.class);
    var calls = new java.util.concurrent.atomic.AtomicInteger();
    doAnswer(call -> {
      Consumer<String> out = call.getArgument(3);
      out.accept(
        calls.getAndIncrement() == 0
          ? "（推过一杯茶）我这儿有柠檬茶。"
          : "难怪你妈说能腌萝卜，这盐确实下得猛。"
      );
      return null;
    })
      .when(models)
      .stream(anyMap(), anyList(), anyList(), any(), anyLong());
    var out = new StringBuilder();
    new ConversationReplyService(models, new ObjectMapper()).stream(
      Map.of(),
      List.of(),
      history("面盐放多了，妈说能腌萝卜"),
      out::append
    );
    assertFalse(out.toString().contains("茶"));
    assertEquals(2, calls.get());
  }

  @Test
  void preservesExplicitSceneStreaming() {
    var models = mock(CompositionService.class);
    doAnswer(call -> {
      Consumer<String> out = call.getArgument(3);
      out.accept("（推过茶杯）");
      return null;
    })
      .when(models)
      .stream(anyMap(), anyList(), anyList(), any(), anyLong());
    var out = new StringBuilder();
    new ConversationReplyService(models, new ObjectMapper()).stream(
      Map.of(),
      List.of(),
      history("来演一段书店避雨的剧情"),
      out::append
    );
    assertEquals("（推过茶杯）", out.toString());
    verify(models, times(1)).stream(anyMap(), anyList(), anyList(), any(), anyLong());
  }

  @Test
  void failedEditorialPassEmitsNothingForExistingRefundPath() {
    var models = mock(CompositionService.class);
    var attempts = new java.util.concurrent.atomic.AtomicInteger();
    doAnswer(call -> {
      if (attempts.getAndIncrement() > 0) throw new IllegalStateException("Editor unavailable");
      Consumer<String> out = call.getArgument(3);
      out.accept("（推过茶杯）我这儿有茶。");
      return null;
    })
      .when(models)
      .stream(anyMap(), anyList(), anyList(), any(), anyLong());
    var out = new StringBuilder();
    assertThrows(IllegalStateException.class, () ->
      new ConversationReplyService(models, new ObjectMapper()).stream(
        Map.of(),
        List.of(),
        history("你好"),
        out::append
      )
    );
    assertEquals("", out.toString());
    verify(models, times(2)).stream(anyMap(), anyList(), anyList(), any(), anyLong());
  }

  @Test
  void preservesExplicitTaskInsteadOfRewritingQuotedStageText() {
    var models = mock(CompositionService.class);
    doAnswer(call -> {
      Consumer<String> out = call.getArgument(3);
      out.accept("翻译：（推过茶杯）");
      return null;
    })
      .when(models)
      .stream(anyMap(), anyList(), anyList(), any(), anyLong());
    var out = new StringBuilder();
    new ConversationReplyService(models, new ObjectMapper()).stream(
      Map.of(),
      List.of(),
      history("翻译这句舞台提示"),
      out::append
    );
    assertEquals("翻译：（推过茶杯）", out.toString());
    verify(models, times(1)).stream(anyMap(), anyList(), anyList(), any(), anyLong());
  }
}
