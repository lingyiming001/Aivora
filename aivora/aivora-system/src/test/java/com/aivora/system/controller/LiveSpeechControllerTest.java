package com.aivora.system.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivora.chat.dto.ChatMediaAudioDTO;
import com.aivora.chat.service.ChatMediaService;
import com.aivora.common.*;
import com.aivora.system.service.LiveWorkspaceService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class LiveSpeechControllerTest {

  @Test
  void endedRoomNeverInvokesPaidModel() {
    var db = mock(JdbcTemplate.class);
    var live = mock(LiveWorkspaceService.class);
    var media = mock(ChatMediaService.class);
    var user = mock(CurrentUser.class);
    when(user.id()).thenReturn(9L);
    when(db.queryForObject(anyString(), eq(String.class), eq(3L))).thenReturn("ENDED");
    assertThrows(ApiException.class, () ->
      new LiveSpeechController(live, media, user, db).transcribe(3, new ChatMediaAudioDTO("audio"))
    );
    verify(live).own(9, 3);
    verifyNoInteractions(media);
  }

  @Test
  void foreignRoomNeverInvokesPaidModel() {
    var db = mock(JdbcTemplate.class);
    var live = mock(LiveWorkspaceService.class);
    var media = mock(ChatMediaService.class);
    var user = mock(CurrentUser.class);
    when(user.id()).thenReturn(9L);
    doThrow(new ApiException(org.springframework.http.HttpStatus.NOT_FOUND, "NOT_FOUND", "missing"))
      .when(live)
      .own(9, 3);
    assertThrows(ApiException.class, () ->
      new LiveSpeechController(live, media, user, db).transcribe(3, new ChatMediaAudioDTO("audio"))
    );
    verifyNoInteractions(media, db);
  }
}
