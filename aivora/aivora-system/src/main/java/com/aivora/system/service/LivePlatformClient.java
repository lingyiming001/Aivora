package com.aivora.system.service;

import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

@Component
public class LivePlatformClient {

  private final ObjectMapper json;
  private final ExecutorService callbacks = Executors.newVirtualThreadPerTaskExecutor();

  @jakarta.annotation.PreDestroy
  public void shutdown() {
    callbacks.shutdownNow();
    http.shutdownNow();
  }

  private final HttpClient http;

  @org.springframework.beans.factory.annotation.Autowired
  public LivePlatformClient(ObjectMapper json) {
    this(json, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
  }

  LivePlatformClient(ObjectMapper json, HttpClient http) {
    this.json = json;
    this.http = http;
  }

  public record Page(List<Message> messages, String cursor, long delay, boolean ended) {}

  public record Message(String id, String author, String content) {}

  public Page youtube(String source, String token, String cursor) throws Exception {
    String url =
      "https://www.googleapis.com/youtube/v3/liveChat/messages?part=snippet,authorDetails&maxResults=200&liveChatId=" +
      enc(source);
    if (!cursor.isBlank()) url += "&pageToken=" + enc(cursor);
    JsonNode data = request(url, token, "", null);
    return youtubePage(data);
  }

  public static Page youtubePage(JsonNode data) {
    List<Message> messages = new ArrayList<>();
    for (JsonNode item : data.path("items")) {
      String text = item.path("snippet").path("displayMessage").asText();
      if (!text.isBlank()) messages.add(
        new Message(
          item.path("id").asText(),
          item.path("authorDetails").path("displayName").asText("观众"),
          text
        )
      );
    }
    return new Page(
      messages,
      data.path("nextPageToken").asText(),
      Math.max(1000, data.path("pollingIntervalMillis").asLong(5000)),
      data.hasNonNull("offlineAt")
    );
  }

  public static Message twitchMessage(JsonNode data) {
    if (
      !"notification".equals(data.path("metadata").path("message_type").asText()) ||
      !"channel.chat.message".equals(data.path("metadata").path("subscription_type").asText())
    ) return null;
    var event = data.path("payload").path("event");
    return new Message(
      event.path("message_id").asText(),
      event.path("chatter_user_name").asText("观众"),
      event.path("message").path("text").asText()
    );
  }

  public AutoCloseable twitch(
    String source,
    String clientId,
    String actor,
    String token,
    Consumer<Message> message,
    Consumer<String> state
  ) throws Exception {
    var socket = new TwitchSocket(source, clientId, actor, token, message, state);
    try {
      socket.open("wss://eventsub.wss.twitch.tv/ws", false);
      return socket;
    } catch (Exception e) {
      socket.close();
      throw e;
    }
  }

  private JsonNode request(String url, String token, String clientId, Object body)
    throws Exception {
    var b = HttpRequest.newBuilder(URI.create(url))
      .timeout(Duration.ofSeconds(20))
      .header("Authorization", "Bearer " + token);
    if (!clientId.isBlank()) b.header("Client-Id", clientId);
    if (body != null) b.header("Content-Type", "application/json").POST(
      HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))
    );
    var r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    if (r.statusCode() / 100 != 2) throw new PlatformFailure(r.statusCode());
    return json.readTree(r.body());
  }

  public static class PlatformFailure extends RuntimeException {

    public final int status;

    public PlatformFailure(int status) {
      super("Platform HTTP " + status);
      this.status = status;
    }
  }

  private static String enc(String s) {
    return URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
  }

  public static boolean validReconnect(String url) {
    try {
      var u = URI.create(url);
      return (
        "wss".equals(u.getScheme()) &&
        "eventsub.wss.twitch.tv".equals(u.getHost()) &&
        u.getUserInfo() == null &&
        (u.getPort() == -1 || u.getPort() == 443)
      );
    } catch (Exception e) {
      return false;
    }
  }

  private final class TwitchSocket implements WebSocket.Listener, AutoCloseable {

    final String source, client, actor, token;
    final Consumer<Message> messages;
    final Consumer<String> state;
    final Map<WebSocket, StringBuilder> buffers = new ConcurrentHashMap<>();
    final Set<WebSocket> sockets = ConcurrentHashMap.newKeySet();
    final Map<WebSocket, Boolean> reconnects = new ConcurrentHashMap<>();
    volatile boolean closed;
    volatile long lastReceived = System.currentTimeMillis();
    volatile long keepalive = 20000;

    TwitchSocket(
      String source,
      String client,
      String actor,
      String token,
      Consumer<Message> messages,
      Consumer<String> state
    ) {
      this.source = source;
      this.client = client;
      this.actor = actor;
      this.token = token;
      this.messages = messages;
      this.state = state;
    }

    void open(String url, boolean reconnect) throws Exception {
      if (!validReconnect(url)) throw new IllegalArgumentException("Invalid Twitch endpoint");
      var ws = http
        .newWebSocketBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .buildAsync(URI.create(url), this)
        .get(20, TimeUnit.SECONDS);
      if (closed) {
        ws.abort();
        return;
      }
      sockets.add(ws);
      reconnects.put(ws, reconnect);
      buffers.put(ws, new StringBuilder());
      Thread.ofVirtual().start(() -> {
        try {
          while (!closed && sockets.contains(ws)) {
            Thread.sleep(5000);
            if (System.currentTimeMillis() - lastReceived > keepalive + 15000) {
              state.accept("RETRYING");
              close();
              return;
            }
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      });
      ws.request(1);
    }

    @Override
    public void onOpen(WebSocket ws) {
      if (closed) ws.abort(); /* demand begins after registration in open() */
    }

    @Override
    public CompletionStage<?> onText(WebSocket ws, CharSequence part, boolean last) {
      lastReceived = System.currentTimeMillis();
      var fragments = buffers.get(ws);
      if (fragments == null || closed) return CompletableFuture.completedFuture(null);
      synchronized (fragments) {
        fragments.append(part);
        if (fragments.length() > 1000000) {
          close();
          state.accept("ERROR");
          return CompletableFuture.completedFuture(null);
        }
        if (!last) {
          ws.request(1);
          return CompletableFuture.completedFuture(null);
        }
      }
      String frame;
      synchronized (fragments) {
        frame = fragments.toString();
        fragments.setLength(0);
      }
      return CompletableFuture.runAsync(() -> {
        try {
          if (closed) return;
          JsonNode data = json.readTree(frame);
          String type = data.path("metadata").path("message_type").asText();
          if ("session_welcome".equals(type)) {
            keepalive =
              Math.max(
                10,
                data.path("payload").path("session").path("keepalive_timeout_seconds").asLong(10)
              ) * 1000;
            if (!Boolean.TRUE.equals(reconnects.get(ws))) request(
              "https://api.twitch.tv/helix/eventsub/subscriptions",
              token,
              client,
              Map.of(
                "type",
                "channel.chat.message",
                "version",
                "1",
                "condition",
                Map.of("broadcaster_user_id", source, "user_id", actor),
                "transport",
                Map.of(
                  "method",
                  "websocket",
                  "session_id",
                  data.path("payload").path("session").path("id").asText()
                )
              )
            );
            if (closed) return;
            for (var old : List.copyOf(sockets))
              if (old != ws) {
                sockets.remove(old);
                buffers.remove(old);
                reconnects.remove(old);
                old.abort();
              }
            state.accept("CONNECTED");
          } else if ("session_reconnect".equals(type)) {
            open(data.path("payload").path("session").path("reconnect_url").asText(), true);
          } else if ("revocation".equals(type)) {
            state.accept("ERROR");
            close();
          } else {
            Message m = twitchMessage(data);
            if (m != null && !m.content().isBlank()) messages.accept(m);
          }
        } catch (PlatformFailure e) {
          state.accept(e.status == 401 || e.status == 403 ? "ERROR" : "RETRYING");
          close();
        } catch (Exception e) {
          if (!closed) state.accept("RETRYING");
          close();
        } finally {
          if (!closed) ws.request(1);
        }
      }, callbacks);
    }

    @Override
    public CompletionStage<?> onClose(WebSocket ws, int code, String reason) {
      boolean active = sockets.remove(ws);
      if (active && !closed) {
        state.accept("RETRYING");
        close();
      }
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public void onError(WebSocket ws, Throwable error) {
      if (!closed && sockets.contains(ws)) {
        state.accept("RETRYING");
        close();
      }
    }

    @Override
    public void close() {
      closed = true;
      for (var ws : sockets) ws.abort();
      sockets.clear();
      reconnects.clear();
      buffers.clear();
    }
  }
}
