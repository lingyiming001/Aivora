package com.aivora.system.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivora.common.ApiException;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class LiveBroadcastServiceTest {

  final JdbcTemplate db = mock(JdbcTemplate.class);
  final LiveWorkspaceService live = mock(LiveWorkspaceService.class);

  LiveBroadcastService service(boolean enabled) {
    return new LiveBroadcastService(
      db,
      live,
      enabled,
      "rtmp://localhost:1935/live",
      "http://127.0.0.1:18085"
    );
  }

  @Test
  void disabledMediaCannotPublishViewOrGenerateKeys() {
    var s = service(false);
    assertFalse(s.canView(UUID.randomUUID().toString()));
    assertThrows(ApiException.class, () -> s.rotate(9, 3));
    verifyNoInteractions(db);
  }

  @Test
  void streamKeysAreStoredAsHashesAndRotatedWithNewViewerLinks() {
    var s = service(true);
    var first = (Map<?, ?>) s.rotate(9, 3);
    var second = (Map<?, ?>) s.rotate(9, 3);
    assertNotEquals(first.get("streamId"), second.get("streamId"));
    var secret = org.mockito.ArgumentCaptor.forClass(String.class);
    verify(db, times(2)).update(
      startsWith("insert into live_broadcast"),
      eq(3L),
      any(UUID.class),
      secret.capture()
    );
    assertEquals(64, secret.getValue().length());
    assertFalse(((String) second.get("streamKey")).contains(secret.getValue()));
    verify(live, times(2)).own(9, 3);
  }

  @Test
  void hooksRequireLiveActiveRoomAndMatchingPublishToken() {
    String id = UUID.randomUUID().toString();
    var s = service(true);
    when(
      db.queryForObject(
        startsWith("select exists(select 1 from live_broadcast b"),
        eq(Boolean.class),
        eq(UUID.fromString(id))
      )
    ).thenReturn(true);
    when(
      db.queryForObject(
        startsWith("select exists(select 1 from live_broadcast where"),
        eq(Boolean.class),
        eq(UUID.fromString(id)),
        anyString()
      )
    ).thenReturn(true);
    assertTrue(s.allowPublish("on_publish", "live", id, "?token=valid&foo=bar"));
    assertFalse(s.allowPublish("on_publish", "live", id, "?token=valid&token=another"));
    assertFalse(s.allowPublish("on_publish", "other", id, "?token=valid"));
    assertFalse(s.allowPublish("on_play", "live", id, "?token=valid"));
    assertFalse(s.allowPublish("on_publish", "live", id, "?other=value"));
    when(
      db.queryForObject(
        startsWith("select exists(select 1 from live_broadcast b"),
        eq(Boolean.class),
        eq(UUID.fromString(id))
      )
    ).thenReturn(false);
    assertFalse(s.allowPublish("on_publish", "live", id, "?token=valid"));
  }

  @Test
  void segmentPathsCannotTraverseOrFetchOtherStreams() {
    String id = UUID.randomUUID().toString();
    assertTrue(LiveBroadcastService.validFile(id, "index.m3u8"));
    assertTrue(LiveBroadcastService.validFile(id, id + "-12.ts"));
    for (String file : List.of(
      "../config",
      "other-1.ts",
      id + "-1xts",
      id + "-1.ts?token=x",
      "https://example.com/a.ts"
    ))
      assertFalse(LiveBroadcastService.validFile(id, file));
    assertThrows(ApiException.class, () -> service(false).segment(id, "index.m3u8"));
  }
}
