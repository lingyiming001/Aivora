package com.aivora.system.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aivora.common.*;
import com.aivora.system.service.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LiveBroadcastHttpTest {

  @Test
  void proxiesManifestAndSegmentsButStopsServingWhenRoomEnds() throws Exception {
    String id = UUID.randomUUID().toString();
    var calls = new AtomicInteger();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/live/", exchange -> {
      calls.incrementAndGet();
      String path = exchange.getRequestURI().getPath();
      String body = path.endsWith(".m3u8")
        ? "#EXTM3U\n#EXTINF:2,\n" + id + "-0.ts\n"
        : "test-video-segment";
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, bytes.length);
      try (var out = exchange.getResponseBody()) {
        out.write(bytes);
      }
    });
    server.start();
    try {
      var db = mock(JdbcTemplate.class);
      when(db.queryForObject(anyString(), eq(Boolean.class), eq(UUID.fromString(id)))).thenReturn(
        true
      );
      var service = new LiveBroadcastService(
        db,
        mock(LiveWorkspaceService.class),
        true,
        "rtmp://localhost/live",
        "http://127.0.0.1:" + server.getAddress().getPort()
      );
      var mvc = MockMvcBuilders.standaloneSetup(
        new LiveBroadcastController(service, mock(CurrentUser.class))
      )
        .setControllerAdvice(new ApiAdvice())
        .build();
      mvc
        .perform(get("/api/live-view/" + id + "/index.m3u8"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(content().string(org.hamcrest.Matchers.containsString(id + "-0.ts")));
      mvc
        .perform(get("/api/live-view/" + id + "/" + id + "-0.ts"))
        .andExpect(status().isOk())
        .andExpect(content().string("test-video-segment"));
      mvc.perform(get("/api/live-view/" + id + "/other-0.ts")).andExpect(status().isNotFound());
      assertEquals(2, calls.get());
      when(db.queryForObject(anyString(), eq(Boolean.class), eq(UUID.fromString(id)))).thenReturn(
        false
      );
      mvc.perform(get("/api/live-view/" + id + "/index.m3u8")).andExpect(status().isNotFound());
      assertEquals(2, calls.get());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void mediaDisabledRejectsPublishHooksWithoutOpeningNetwork() throws Exception {
    var db = mock(JdbcTemplate.class);
    var service = new LiveBroadcastService(
      db,
      mock(LiveWorkspaceService.class),
      false,
      "rtmp://localhost/live",
      "http://127.0.0.1:1"
    );
    var mvc = MockMvcBuilders.standaloneSetup(
      new LiveBroadcastController(service, mock(CurrentUser.class))
    ).build();
    mvc
      .perform(
        post("/api/live-media/publish")
          .contentType("application/json")
          .content(
            "{\"action\":\"on_publish\",\"app\":\"live\",\"stream\":\"invalid\",\"param\":\"?token=x\"}"
          )
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value(1));
    verifyNoInteractions(db);
  }
}
