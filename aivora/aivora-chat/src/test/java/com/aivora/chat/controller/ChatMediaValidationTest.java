package com.aivora.chat.controller;

import static org.junit.jupiter.api.Assertions.*;

import com.aivora.common.ApiException;
import java.util.*;
import org.junit.jupiter.api.Test;

class ChatMediaValidationTest {

  @Test
  void rejectsUntrustedAudioSourcesAndSize() {
    for (String value : List.of(
      "https://example.com/audio.wav",
      "data:audio/svg;base64,AAAA",
      "data:audio/wav;base64,aGVsbG8="
    ))
      assertThrows(ApiException.class, () -> ChatMediaController.validateAudio(value));
    String valid = "data:audio/wav;base64," + Base64.getEncoder().encodeToString(new byte[44]);
    assertEquals(valid, ChatMediaController.validateAudio(valid));
  }

  @Test
  void onlyProviderMediaUrlsAccepted() {
    assertEquals(
      "https://dashscope-result-bj.oss-cn-beijing.aliyuncs.com/test.wav",
      ChatMediaController.safeMediaUrl(
        "http://dashscope-result-bj.oss-cn-beijing.aliyuncs.com/test.wav"
      )
    );
    for (String url : List.of(
      "https://evil.example/audio.wav",
      "https://aliyuncs.com.evil.example/a",
      "file:///etc/passwd",
      "https://user@foo.aliyuncs.com/a"
    ))
      assertThrows(ApiException.class, () -> ChatMediaController.safeMediaUrl(url));
  }
}
