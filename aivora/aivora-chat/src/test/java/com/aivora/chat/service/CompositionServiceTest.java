package com.aivora.chat.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivora.ai.domain.AiGateway;
import com.aivora.ai.infrastructure.SecretCipher;
import com.aivora.common.ApiException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class CompositionServiceTest {

  Map<String, Object> model(String name) {
    return Map.of(
      "id",
      1L,
      "kind",
      "OPENAI_COMPATIBLE",
      "base_url",
      "https://dashscope.aliyuncs.com/compatible-mode/v1",
      "model_name",
      name
    );
  }

  List<AiGateway.Message> history() {
    return List.of(
      new AiGateway.Message("system", "角色设定"),
      new AiGateway.Message("user", "写一份学习计划")
    );
  }

  @Test
  void onlyAuthorizedOfficialHostAllowed() {
    assertDoesNotThrow(() -> CompositionService.requireApprovedProvider(model("main")));
    for (String url : List.of(
      "https://api.deepseek.com/v1",
      "https://dashscope.aliyuncs.com.evil.test/v1",
      "http://dashscope.aliyuncs.com/v1",
      "https://dashscope.aliyuncs.com:8443/v1",
      "https://user@dashscope.aliyuncs.com/v1"
    )) {
      var m = new HashMap<>(model("main"));
      m.put("base_url", url);
      assertEquals(
        "COMPOSITION_PROVIDER_DENIED",
        assertThrows(ApiException.class, () -> CompositionService.requireApprovedProvider(m)).code()
      );
    }
  }

  @Test
  void helperSuggestionsReachMainButNotClient() {
    var seen = new CopyOnWriteArrayList<String>();
    var output = new StringBuilder();
    AiGateway gateway = (url, key, name, messages, delta) -> {
      seen.add(name);
      if (name.equals("main")) {
        assertTrue(messages.get(1).content().contains("建议一"));
        assertTrue(messages.get(1).content().contains("建议二"));
        assertEquals("角色设定", messages.getFirst().content());
        assertEquals("写一份学习计划", messages.getLast().content());
        delta.accept("最终回复");
      } else delta.accept(name.equals("helper1") ? "建议一" : "建议二");
    };
    new CompositionService(mock(JdbcTemplate.class), gateway, mock(SecretCipher.class)).stream(
      model("main"),
      List.of(model("helper1"), model("helper2")),
      history(),
      output::append
    );
    assertEquals("最终回复", output.toString());
    assertEquals(3, seen.size());
    assertEquals("main", seen.getLast());
  }

  @Test
  void helperFailureStopsMain() {
    var seen = new CopyOnWriteArrayList<String>();
    AiGateway gateway = (url, key, name, messages, delta) -> {
      seen.add(name);
      throw new IllegalStateException("供应商失败");
    };
    assertThrows(IllegalStateException.class, () ->
      new CompositionService(mock(JdbcTemplate.class), gateway, mock(SecretCipher.class)).stream(
        model("main"),
        List.of(model("helper1")),
        history(),
        s -> fail("辅助内容不得发送给用户")
      )
    );
    assertEquals(List.of("helper1"), seen);
  }

  @Test
  void singleModelKeepsOriginalContext() {
    AiGateway gateway = (url, key, name, messages, delta) -> {
      assertEquals(history(), messages);
      delta.accept("单模型");
    };
    var output = new StringBuilder();
    new CompositionService(mock(JdbcTemplate.class), gateway, mock(SecretCipher.class)).stream(
      model("main"),
      List.of(),
      history(),
      output::append
    );
    assertEquals("单模型", output.toString());
  }

  @Test
  void belowThresholdDoesNotPrepareHelpers() throws Exception {
    var db = mock(JdbcTemplate.class);
    var array = mock(java.sql.Array.class);
    when(array.getArray()).thenReturn(new Long[] { 2L });
    var m = new HashMap<>(model("main"));
    m.put("helper_model_ids", array);
    m.put("min_characters", 100);
    var route = new RoutingService.Selection(1, 3, "VIP", Map.of(), m);
    assertTrue(
      new CompositionService(db, mock(AiGateway.class), mock(SecretCipher.class))
        .prepare(route, "短消息")
        .isEmpty()
    );
    verifyNoInteractions(db);
  }

  @Test
  void helperAvailabilityCheckedBeforeCalling() throws Exception {
    var db = mock(JdbcTemplate.class);
    var array = mock(java.sql.Array.class);
    when(array.getArray()).thenReturn(new Long[] { 2L });
    var m = new HashMap<>(model("main"));
    m.put("helper_model_ids", array);
    var gateway = mock(AiGateway.class);
    assertEquals(
      "COMPOSITION_UNAVAILABLE",
      assertThrows(ApiException.class, () ->
        new CompositionService(db, gateway, mock(SecretCipher.class)).prepare(
          new RoutingService.Selection(1, 3, "VIP", Map.of(), m),
          "问题"
        )
      ).code()
    );
    verifyNoInteractions(gateway);
  }

  @Test
  void mainFailurePropagatesForRefund() {
    AiGateway gateway = (url, key, name, messages, delta) -> {
      if (name.equals("main")) throw new IllegalStateException("主模型失败");
      delta.accept("建议");
    };
    assertThrows(IllegalStateException.class, () ->
      new CompositionService(mock(JdbcTemplate.class), gateway, mock(SecretCipher.class)).stream(
        model("main"),
        List.of(model("helper1")),
        history(),
        s -> {}
      )
    );
  }

  @Test
  void demoUnavailableOutsideDev() {
    var gateway = mock(AiGateway.class);
    assertThrows(IllegalStateException.class, () ->
      new CompositionService(mock(JdbcTemplate.class), gateway, mock(SecretCipher.class)).stream(
        Map.of("kind", "LOCAL_DEMO"),
        List.of(),
        history(),
        s -> {}
      )
    );
    verifyNoInteractions(gateway);
  }

  @Test
  void unapprovedSingleModelCannotSendData() {
    var gateway = mock(AiGateway.class);
    var m = new HashMap<>(model("main"));
    m.put("base_url", "https://api.deepseek.com/v1");
    assertThrows(IllegalStateException.class, () ->
      new CompositionService(mock(JdbcTemplate.class), gateway, mock(SecretCipher.class)).stream(
        m,
        List.of(),
        history(),
        s -> {}
      )
    );
    verifyNoInteractions(gateway);
  }
}
