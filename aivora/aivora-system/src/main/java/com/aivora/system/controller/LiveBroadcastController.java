package com.aivora.system.controller;

import com.aivora.common.CurrentUser;
import com.aivora.system.service.LiveBroadcastService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class LiveBroadcastController {

  private final LiveBroadcastService media;
  private final CurrentUser current;

  public LiveBroadcastController(LiveBroadcastService media, CurrentUser current) {
    this.media = media;
    this.current = current;
  }

  public record Hook(
    @NotNull @Size(max = 30) String action,
    @NotNull @Size(max = 50) String app,
    @NotNull @Size(max = 100) String stream,
    @NotNull @Size(max = 2000) String param
  ) {}

  @GetMapping("/api/live/rooms/{id}/broadcast")
  public Object config(@PathVariable long id) {
    return media.config(current.id(), id);
  }

  @PostMapping("/api/live/rooms/{id}/broadcast")
  public Object rotate(@PathVariable long id) {
    return media.rotate(current.id(), id);
  }

  @PostMapping("/api/live-media/publish")
  public Object publish(@Valid @RequestBody Hook hook) {
    return Map.of(
      "code",
      media.allowPublish(hook.action(), hook.app(), hook.stream(), hook.param()) ? 0 : 1
    );
  }

  @GetMapping("/api/live-view/{stream}/{file}")
  public ResponseEntity<byte[]> view(@PathVariable String stream, @PathVariable String file) {
    byte[] data = media.segment(stream, file);
    return ResponseEntity.ok()
      .cacheControl(CacheControl.noStore())
      .header("X-Content-Type-Options", "nosniff")
      .contentType(
        MediaType.parseMediaType(
          file.endsWith(".m3u8") ? "application/vnd.apple.mpegurl" : "video/mp2t"
        )
      )
      .body(data);
  }
}
