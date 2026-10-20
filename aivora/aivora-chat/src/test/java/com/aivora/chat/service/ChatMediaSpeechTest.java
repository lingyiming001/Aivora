package com.aivora.chat.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivora.ai.infrastructure.SecretCipher;
import com.aivora.chat.dto.ChatMediaSpeechDTO;
import com.aivora.common.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;

class ChatMediaSpeechTest {

  JdbcTemplate db = mock(JdbcTemplate.class);
  CurrentUser current = mock(CurrentUser.class);
  ChatSpeechStore store = mock(ChatSpeechStore.class);
  ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
  ChatMediaService service = new ChatMediaService(
    db,
    current,
    mock(SecretCipher.class),
    new ObjectMapper(),
    publisher,
    mock(ChatImageService.class),
    store
  );

  void own() {
    when(current.id()).thenReturn(7L);
    when(db.queryForList(startsWith("select s.id"), eq(5L), eq(7L))).thenReturn(
      List.of(Map.of("id", 5L))
    );
    when(
      db.queryForList(startsWith("select content"), eq(String.class), eq(9L), eq(5L), eq(5L))
    ).thenReturn(List.of("你好"));
    when(store.profile(5L)).thenReturn(
      Map.of(
        "model_name",
        "qwen3-tts-flash",
        "speech_voice",
        "Cherry",
        "speech_language",
        "Chinese",
        "available",
        false
      )
    );
  }

  @Test
  void servesSavedAudioEvenIfPinnedModelHasBecomeUnavailable() {
    own();
    when(store.cached(eq(9L), anyString())).thenReturn(true);
    var response = (Map<?, ?>) service.speech(5L, new ChatMediaSpeechDTO(9L));
    assertEquals("Cherry", response.get("voice"));
    assertEquals("Chinese", response.get("language"));
    assertEquals("/chats/5/media/speech/9/audio", response.get("audioUrl"));
    verifyNoInteractions(publisher);
  }

  @Test
  void doesNotSwitchToAnotherModelWhenPinnedModelIsUnavailable() {
    own();
    when(store.cached(eq(9L), anyString())).thenReturn(false);
    assertEquals(
      "VOICE_UNAVAILABLE",
      assertThrows(ApiException.class, () -> service.speech(5L, new ChatMediaSpeechDTO(9L))).code()
    );
    verifyNoInteractions(publisher);
  }

  @Test
  void audioRouteChecksOwnerBeforeReadingSavedBytes() {
    when(current.id()).thenReturn(8L);
    assertThrows(ApiException.class, () -> service.speechAudio(5L, 9L));
    verifyNoInteractions(store);
  }
}
