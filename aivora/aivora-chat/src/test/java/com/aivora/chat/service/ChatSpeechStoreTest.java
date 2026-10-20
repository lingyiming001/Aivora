package com.aivora.chat.service;

import static org.junit.jupiter.api.Assertions.*;

import com.aivora.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;

class ChatSpeechStoreTest {

  @Test
  void cleansFormattingWithoutChangingSpokenWords() {
    var result = ChatSpeechStore.text(
      "## **你好**，看看[这本书](https://example.invalid)。`明天见`。"
    );
    assertEquals("你好，看看这本书。明天见。", result.value());
    assertFalse(result.truncated());
  }

  @Test
  void capsWithoutSplittingSurrogatePairs() {
    var result = ChatSpeechStore.text("字".repeat(599) + "😀");
    assertEquals(599, result.value().length());
    assertTrue(result.truncated());
    assertEquals(600, ChatSpeechStore.text("字".repeat(800)).value().length());
  }

  @Test
  void cacheKeyIncludesExactVoiceLanguageModelAndText() {
    var p = new HashMap<String, Object>(
      Map.of(
        "model_name",
        "qwen3-tts-flash",
        "speech_voice",
        "Cherry",
        "speech_language",
        "Chinese"
      )
    );
    String hash = ChatSpeechStore.hash("你好", p);
    assertEquals(hash, ChatSpeechStore.hash("你好", p));
    p.put("speech_voice", "Serena");
    assertNotEquals(hash, ChatSpeechStore.hash("你好", p));
    p.put("speech_voice", "Cherry");
    p.put("speech_language", "English");
    assertNotEquals(hash, ChatSpeechStore.hash("你好", p));
    p.put("speech_language", "Chinese");
    p.put("model_name", "different");
    assertNotEquals(hash, ChatSpeechStore.hash("你好", p));
    p.put("model_name", "qwen3-tts-flash");
    assertNotEquals(hash, ChatSpeechStore.hash("再见", p));
  }

  @Test
  void rejectsMissingOrChangedVoiceRatherThanSwitchingModel() {
    assertThrows(ApiException.class, () ->
      ChatSpeechStore.requireAvailable(Map.of("available", false))
    );
    assertDoesNotThrow(() -> ChatSpeechStore.requireAvailable(Map.of("available", true)));
  }

  @Test
  void rejectsProviderErrorDocumentsInsteadOfSavingThemAsAudio() {
    assertThrows(ApiException.class, () ->
      ChatSpeechStore.validateAudio("<html>error</html>".getBytes(StandardCharsets.UTF_8))
    );
    byte[] wav = new byte[44];
    System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, wav, 0, 4);
    System.arraycopy("WAVE".getBytes(StandardCharsets.US_ASCII), 0, wav, 8, 4);
    assertDoesNotThrow(() -> ChatSpeechStore.validateAudio(wav));
  }
}
