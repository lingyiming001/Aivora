package com.aivora.system.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivora.common.ApiException;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class LiveWorkspaceServiceTest {

  @Test
  void refusesOtherUsersRoomBeforeReadingEvents() {
    var db = mock(JdbcTemplate.class);
    var live = new LiveWorkspaceService(db);
    when(db.queryForObject(anyString(), eq(Boolean.class), eq(3L), eq(9L))).thenReturn(false);
    assertThrows(ApiException.class, () -> live.publish(9, 3, "CHAT", "x", "hello", null));
    verify(db, never()).queryForMap(anyString(), any(Object.class));
  }

  @Test
  void rejectsMessagesOutsideLiveState() {
    var db = mock(JdbcTemplate.class);
    var live = new LiveWorkspaceService(db);
    when(db.queryForObject(anyString(), eq(Boolean.class), eq(3L), eq(9L))).thenReturn(true);
    when(db.queryForMap(anyString(), eq(3L))).thenReturn(
      Map.of("status", "ENDED", "plugins", "trim")
    );
    assertThrows(ApiException.class, () -> live.publish(9, 3, "CHAT", "x", "hello", null));
    verify(db, never()).queryForList(
      startsWith("insert"),
      eq(Long.class),
      any(),
      any(),
      any(),
      any(),
      any()
    );
  }

  @Test
  void duplicateExternalIdsAreNotRepublished() {
    var db = mock(JdbcTemplate.class);
    var live = new LiveWorkspaceService(db);
    when(db.queryForObject(anyString(), eq(Boolean.class), eq(3L), eq(9L))).thenReturn(true);
    when(db.queryForMap(anyString(), eq(3L))).thenReturn(
      Map.of("status", "LIVE", "plugins", "trim,hide-links")
    );
    when(
      db.queryForList(
        startsWith("insert"),
        eq(Long.class),
        eq(3L),
        eq("event1"),
        eq("CHAT"),
        eq("x"),
        eq("hello [链接]")
      )
    ).thenReturn(List.of());
    assertEquals(
      Map.of("duplicate", true),
      live.publish(9, 3, "CHAT", "x", " hello  https://example.com ", "event1")
    );
  }

  @Test
  void unknownPluginConfigurationFails() {
    var db = mock(JdbcTemplate.class);
    var live = new LiveWorkspaceService(db);
    when(db.queryForObject(anyString(), eq(Boolean.class), eq(3L), eq(9L))).thenReturn(true);
    assertThrows(IllegalArgumentException.class, () ->
      live.configure(9, 3, "LIVE", List.of("remote-code"))
    );
  }

  @Test
  void overlayRejectsInvalidToken() {
    var db = mock(JdbcTemplate.class);
    var live = new LiveWorkspaceService(db);
    assertThrows(ApiException.class, () -> live.overlay(3, "invalid", 0));
    verify(db, never()).queryForList(startsWith("select id,kind"), eq(3L), eq(0L));
  }

  @Test
  void discoversTrustedPluginsAndRejectsDuplicateIds() {
    var plugin = new LivePluginConfiguration.TextPlugin("custom", "自定义", "测试", text ->
      text.toUpperCase()
    );
    var service = new LiveWorkspaceService(mock(JdbcTemplate.class), List.of(plugin));
    assertEquals("custom", service.plugins().getFirst().get("id"));
    assertThrows(IllegalArgumentException.class, () ->
      new LiveWorkspaceService(mock(JdbcTemplate.class), List.of(plugin, plugin))
    );
  }

  @Test
  void pluginChainCleansTextWithoutInterpretingHtml() {
    assertEquals("hello world", LiveWorkspaceService.apply("trim", " hello \n world "));
    assertEquals(
      "<b>x</b> [链接]",
      LiveWorkspaceService.apply("hide-links", "<b>x</b> https://example.com/path")
    );
  }
}
