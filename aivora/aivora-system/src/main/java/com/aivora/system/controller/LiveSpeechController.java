package com.aivora.system.controller;

import com.aivora.chat.dto.ChatMediaAudioDTO;
import com.aivora.chat.service.ChatMediaService;
import com.aivora.common.*;
import com.aivora.system.service.LiveWorkspaceService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/live/rooms/{id}/speech")
public class LiveSpeechController {

  private final LiveWorkspaceService live;
  private final ChatMediaService media;
  private final CurrentUser current;
  private final JdbcTemplate db;

  public LiveSpeechController(
    LiveWorkspaceService live,
    ChatMediaService media,
    CurrentUser current,
    JdbcTemplate db
  ) {
    this.live = live;
    this.media = media;
    this.current = current;
    this.db = db;
  }

  @GetMapping
  public Object capability(@PathVariable long id) {
    live.own(current.id(), id);
    return Map.of("asr", media.transcriptionAvailable());
  }

  @PostMapping
  public Object transcribe(@PathVariable long id, @Valid @RequestBody ChatMediaAudioDTO audio) {
    live.own(current.id(), id);
    if (
      !"LIVE".equals(db.queryForObject("select status from live_room where id=?", String.class, id))
    ) throw new ApiException(HttpStatus.CONFLICT, "ROOM_NOT_LIVE", "请先开始互动");
    return media.transcribeForCurrentUser(audio);
  }
}
