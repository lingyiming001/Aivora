package com.aivora.system.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivora.ai.infrastructure.SecretCipher;
import com.aivora.common.ApiException;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;

class LiveConnectionServiceTest {

  final JdbcTemplate db = mock(JdbcTemplate.class);
  final SecretCipher cipher = mock(SecretCipher.class);
  final LiveWorkspaceService live = mock(LiveWorkspaceService.class);
  final LivePlatformClient client = mock(LivePlatformClient.class);
  final LiveConnectionService service = new LiveConnectionService(db, cipher, live, client);

  @AfterEach
  void close() {
    service.close();
  }

  @Test
  void savingEncryptsTokenAndNeverReturnsIt() {
    when(db.queryForObject(startsWith("select platform"), eq(String.class), eq(3L))).thenReturn(
      "youtube"
    );
    when(db.queryForList(startsWith("select token_ciphertext"), eq(3L))).thenReturn(List.of());
    when(cipher.encrypt("secret")).thenReturn("encrypted");
    service.save(9, 3, "chat-id", "", "", "secret");
    verify(live).own(9, 3);
    verify(cipher).encrypt("secret");
    verify(db).update(
      startsWith("insert into live_connection"),
      eq(3L),
      eq("chat-id"),
      eq(""),
      eq(""),
      eq("encrypted"),
      anyString()
    );
  }

  @Test
  void blankTokenPreservesExistingCiphertext() {
    when(db.queryForObject(startsWith("select platform"), eq(String.class), eq(3L))).thenReturn(
      "youtube"
    );
    when(db.queryForList(startsWith("select token_ciphertext"), eq(3L))).thenReturn(
      List.of(Map.of("token_ciphertext", "encrypted"))
    );
    service.save(9, 3, "chat-id", "", "", "");
    verifyNoInteractions(cipher);
  }

  @Test
  void missingOrUnsupportedConfigurationNeverConnects() {
    when(db.queryForObject(startsWith("select platform"), eq(String.class), eq(3L))).thenReturn(
      "douyin"
    );
    assertThrows(ApiException.class, () -> service.save(9, 3, "id", "", "", "token"));
    verifyNoInteractions(client, cipher);
    when(db.queryForObject(startsWith("select status"), eq(String.class), eq(3L))).thenReturn(
      "ENDED"
    );
    assertThrows(ApiException.class, () -> service.connect(9, 3));
  }

  @Test
  void unauthorizedOwnerIsRejectedBeforeTokenReadOrNetworkCall() {
    doThrow(new ApiException(org.springframework.http.HttpStatus.NOT_FOUND, "NOT_FOUND", "missing"))
      .when(live)
      .own(9, 3);
    assertThrows(ApiException.class, () -> service.save(9, 3, "chat", "", "", "token"));
    verifyNoInteractions(db, cipher, client);
  }

  @Test
  void workerPublishesNormalizedMessageAndStopsOnExpiredAuthorization() throws Exception {
    var config = new HashMap<String, Object>();
    config.putAll(
      Map.of(
        "room_id",
        3L,
        "user_id",
        9L,
        "revision",
        "revision",
        "platform",
        "youtube",
        "source_id",
        "chat",
        "client_id",
        "",
        "actor_id",
        "",
        "token_ciphertext",
        "encrypted"
      )
    );
    when(db.queryForList(startsWith("select c.*"))).thenReturn(List.of(config));
    when(
      db.queryForObject(startsWith("select exists"), eq(Boolean.class), eq(3L), eq("revision"))
    ).thenReturn(true);
    when(cipher.decrypt("encrypted")).thenReturn("token");
    when(client.youtube("chat", "token", "")).thenReturn(
      new LivePlatformClient.Page(
        List.of(new LivePlatformClient.Message("message-id", "viewer", "hello")),
        "cursor",
        1,
        false
      )
    );
    when(client.youtube("chat", "token", "cursor")).thenThrow(
      new LivePlatformClient.PlatformFailure(401)
    );
    service.tick();
    verify(live, timeout(3000)).publish(
      eq(9L),
      eq(3L),
      eq("CHAT"),
      eq("viewer"),
      eq("hello"),
      startsWith("youtube:")
    );
    verify(db, timeout(3000)).update(
      startsWith("update live_connection set status="),
      eq("ERROR"),
      anyString(),
      eq(3L),
      eq("revision")
    );
  }
}
