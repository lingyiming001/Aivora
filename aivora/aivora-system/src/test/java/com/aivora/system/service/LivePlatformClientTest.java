package com.aivora.system.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LivePlatformClientTest {

  final ObjectMapper json = new ObjectMapper();

  @Test
  void twitchSubscribesAfterWelcomeAndDeliversChat() throws Exception {
    var http = mock(HttpClient.class);
    var builder = mock(WebSocket.Builder.class);
    var socket = mock(WebSocket.class);
    when(http.newWebSocketBuilder()).thenReturn(builder);
    when(builder.connectTimeout(any())).thenReturn(builder);
    when(builder.buildAsync(any(), any())).thenReturn(
      java.util.concurrent.CompletableFuture.completedFuture(socket)
    );
    HttpResponse<String> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(202);
    when(response.body()).thenReturn("{}");
    doReturn(response).when(http).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    var client = new LivePlatformClient(json, http);
    java.util.function.Consumer<LivePlatformClient.Message> messages = mock(
      java.util.function.Consumer.class
    );
    java.util.function.Consumer<String> state = mock(java.util.function.Consumer.class);
    AutoCloseable connection = null;
    try {
      connection = client.twitch("123", "client", "456", "token", messages, state);
      var listener = ArgumentCaptor.forClass(WebSocket.Listener.class);
      verify(builder).buildAsync(any(), listener.capture());
      listener
        .getValue()
        .onText(
          socket,
          "{\"metadata\":{\"message_type\":\"session_welcome\"},\"payload\":{\"session\":{\"id\":\"session-1\",\"keepalive_timeout_seconds\":10}}}",
          true
        )
        .toCompletableFuture()
        .get(3, java.util.concurrent.TimeUnit.SECONDS);
      var request = ArgumentCaptor.forClass(HttpRequest.class);
      verify(http).send(request.capture(), any(HttpResponse.BodyHandler.class));
      assertEquals(
        "https://api.twitch.tv/helix/eventsub/subscriptions",
        request.getValue().uri().toString()
      );
      assertEquals("POST", request.getValue().method());
      assertEquals("client", request.getValue().headers().firstValue("Client-Id").orElseThrow());
      verify(state).accept("CONNECTED");
      listener
        .getValue()
        .onText(
          socket,
          "{\"metadata\":{\"message_type\":\"notification\",\"subscription_type\":\"channel.chat.message\"},\"payload\":{\"event\":{\"message_id\":\"m1\",\"chatter_user_name\":\"viewer\",\"message\":{\"text\":\"hello\"}}}}",
          true
        )
        .toCompletableFuture()
        .get(3, java.util.concurrent.TimeUnit.SECONDS);
      verify(messages).accept(new LivePlatformClient.Message("m1", "viewer", "hello"));
      listener.getValue().onClose(socket, 1006, "closed");
      verify(state).accept("RETRYING");
    } finally {
      if (connection != null) connection.close();
      client.shutdown();
    }
  }

  @Test
  void youtubeDecodesMessagesCursorAndPlatformInterval() throws Exception {
    var page = LivePlatformClient.youtubePage(
      json.readTree(
        """
        {"items":[{"id":"m1","snippet":{"displayMessage":"hello"},"authorDetails":{"displayName":"viewer"}},{"id":"m2","snippet":{}}],"nextPageToken":"next","pollingIntervalMillis":12345}
        """
      )
    );
    assertEquals(1, page.messages().size());
    assertEquals("viewer", page.messages().getFirst().author());
    assertEquals("next", page.cursor());
    assertEquals(12345, page.delay());
    assertFalse(page.ended());
    assertTrue(
      LivePlatformClient.youtubePage(
        json.readTree("{\"offlineAt\":\"2026-10-18T00:00:00Z\"}")
      ).ended()
    );
  }

  @Test
  void twitchDecodesOnlyChatNotifications() throws Exception {
    var m = LivePlatformClient.twitchMessage(
      json.readTree(
        """
        {"metadata":{"message_type":"notification","subscription_type":"channel.chat.message"},"payload":{"event":{"message_id":"m1","chatter_user_name":"viewer","message":{"text":"hello"}}}}
        """
      )
    );
    assertEquals("hello", m.content());
    assertEquals("m1", m.id());
    assertNull(
      LivePlatformClient.twitchMessage(
        json.readTree("{\"metadata\":{\"message_type\":\"session_keepalive\"}}")
      )
    );
  }

  @Test
  void twitchReconnectCannotRedirectCredentialsOrSocketsToOtherHosts() {
    assertTrue(
      LivePlatformClient.validReconnect("wss://eventsub.wss.twitch.tv/ws?session=example")
    );
    for (String url : java.util.List.of(
      "wss://evil.example/ws",
      "ws://eventsub.wss.twitch.tv/ws",
      "wss://user:pass@eventsub.wss.twitch.tv/ws",
      "wss://eventsub.wss.twitch.tv:8443/ws",
      "https://eventsub.wss.twitch.tv/ws"
    ))
      assertFalse(LivePlatformClient.validReconnect(url));
  }

  @Test
  void youtubeUsesFixedHttpsHostAndBearerHeaderRatherThanUrlToken() throws Exception {
    var http = mock(HttpClient.class);
    HttpResponse<String> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(200);
    when(response.body()).thenReturn("{\"items\":[],\"nextPageToken\":\"next\"}");
    doReturn(response).when(http).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    var client = new LivePlatformClient(json, http);
    try {
      client.youtube("chat / id", "secret-token", "cursor ?");
      var request = ArgumentCaptor.forClass(HttpRequest.class);
      verify(http).send(request.capture(), any(HttpResponse.BodyHandler.class));
      assertEquals("www.googleapis.com", request.getValue().uri().getHost());
      assertEquals("https", request.getValue().uri().getScheme());
      assertFalse(request.getValue().uri().toString().contains("secret-token"));
      assertTrue(request.getValue().uri().toString().contains("pageToken=cursor+%3F"));
      assertEquals(
        "Bearer secret-token",
        request.getValue().headers().firstValue("Authorization").orElseThrow()
      );
      when(response.statusCode()).thenReturn(401);
      assertThrows(LivePlatformClient.PlatformFailure.class, () ->
        client.youtube("id", "token", "")
      );
    } finally {
      client.shutdown();
    }
  }
}
