package com.aivora.system.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivora.ai.infrastructure.SecretCipher;
import com.aivora.common.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ModelAdminServiceTest {

  @Test
  void invalidUrlsCannotCarryCredentials() {
    for (String url : List.of(
      "file:///tmp/key",
      "https://user:pass@example.com/v1",
      "https://api.example.com?key=secret"
    ))
      assertThrows(ApiException.class, () -> ModelAdminService.validateUrl(url));
    assertEquals(
      "https://example.com/v1",
      ModelAdminService.validateUrl("https://example.com/v1/")
    );
  }

  @Test
  void blankKeyPreservesEncryptedKey() {
    JdbcTemplate db = mock(JdbcTemplate.class);
    when(db.queryForList(startsWith("select api_key_ciphertext"), eq(1L))).thenReturn(
      List.of(Map.of("api_key_ciphertext", "encrypted"))
    );
    new ModelAdminService(db, mock(SecretCipher.class), new ObjectMapper()).provider(
      1L,
      "https://example.com/v1",
      "",
      true,
      false,
      7L
    );
    verify(db).update(
      startsWith("update ai_provider"),
      eq("https://example.com/v1"),
      eq("encrypted"),
      eq(true),
      eq(1L)
    );
  }

  @Test
  void cannotEnableWithoutKey() {
    JdbcTemplate db = mock(JdbcTemplate.class);
    when(db.queryForList(startsWith("select api_key_ciphertext"), eq(1L))).thenReturn(
      List.of(new HashMap<>())
    );
    assertEquals(
      "KEY_REQUIRED",
      assertThrows(ApiException.class, () ->
        new ModelAdminService(db, mock(SecretCipher.class), new ObjectMapper()).provider(
          1L,
          "https://example.com",
          null,
          true,
          false,
          7L
        )
      ).code()
    );
  }

  @Test
  void defaultCannotPointToDisabledModel() {
    JdbcTemplate db = mock(JdbcTemplate.class);
    assertEquals(
      "UNAVAILABLE",
      assertThrows(ApiException.class, () ->
        new ModelAdminService(db, mock(SecretCipher.class), new ObjectMapper()).model(
          1L,
          2L,
          "model",
          1,
          false,
          true,
          7L
        )
      ).code()
    );
    verify(db, never()).update(startsWith("update ai_model set is_default=false"));
  }

  @Test
  void voiceCannotBeChatDefault() {
    var service = new ModelAdminService(
      mock(JdbcTemplate.class),
      mock(SecretCipher.class),
      new ObjectMapper()
    );
    assertEquals(
      "INVALID_MODEL_TYPE",
      assertThrows(ApiException.class, () ->
        service.model(null, 1, "speech", 0, true, true, "VOICE", 7)
      ).code()
    );
  }

  @Test
  void rejectsUnknownModelType() {
    var service = new ModelAdminService(
      mock(JdbcTemplate.class),
      mock(SecretCipher.class),
      new ObjectMapper()
    );
    assertEquals(
      "INVALID_MODEL_TYPE",
      assertThrows(ApiException.class, () ->
        service.model(null, 1, "speech", 0, true, false, "UNKNOWN", 7)
      ).code()
    );
  }

  @Test
  void voiceCannotReplaceModelReferencedByRoute() {
    JdbcTemplate db = mock(JdbcTemplate.class);
    when(
      db.queryForObject(
        startsWith("select count(*) from model_route_rule"),
        eq(Integer.class),
        isNull(),
        eq(1L),
        eq("speech")
      )
    ).thenReturn(1);
    var service = new ModelAdminService(db, mock(SecretCipher.class), new ObjectMapper());
    assertEquals(
      "MODEL_IN_ROUTE",
      assertThrows(ApiException.class, () ->
        service.model(null, 1, "speech", 0, true, false, "VOICE", 7)
      ).code()
    );
    verify(db, never()).update(startsWith("insert into ai_model"));
  }

  @Test
  void voiceProbeDoesNotCallTextEndpoint() {
    JdbcTemplate db = mock(JdbcTemplate.class);
    when(db.queryForList(startsWith("select m.model_type"), eq(2L))).thenReturn(
      List.of(Map.of("model_type", "VOICE"))
    );
    var cipher = mock(SecretCipher.class);
    var service = new ModelAdminService(db, cipher, new ObjectMapper());
    assertEquals(
      "VOICE_TEST_UNSUPPORTED",
      assertThrows(ApiException.class, () -> service.probe(2)).code()
    );
    verifyNoInteractions(cipher);
  }
}
