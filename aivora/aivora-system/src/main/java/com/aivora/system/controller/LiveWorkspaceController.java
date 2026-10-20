package com.aivora.system.controller;

import com.aivora.common.CurrentUser;
import com.aivora.system.service.LiveWorkspaceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class LiveWorkspaceController {

  @org.springframework.web.bind.annotation.ExceptionHandler(IllegalArgumentException.class)
  @org.springframework.web.bind.annotation.ResponseStatus(
    org.springframework.http.HttpStatus.BAD_REQUEST
  )
  public Object invalid(IllegalArgumentException ex) {
    return Map.of("code", "INVALID_LIVE_INPUT", "message", "直播配置或消息参数无效");
  }

  private final LiveWorkspaceService live;
  private final CurrentUser current;

  public LiveWorkspaceController(LiveWorkspaceService live, CurrentUser current) {
    this.live = live;
    this.current = current;
  }

  public record Room(
    @NotBlank @Size(max = 100) String name,
    @NotBlank String platform,
    @NotNull @Size(max = 200) String externalRoom
  ) {}

  public record Settings(@NotBlank String status, @NotNull List<@NotBlank String> plugins) {}

  public record Event(
    @NotBlank String kind,
    @NotNull @Size(max = 80) String author,
    @NotBlank @Size(max = 2000) String content,
    @Size(max = 150) String externalId
  ) {}

  @GetMapping("/api/live/catalog")
  public Object catalog() {
    return Map.of("platforms", live.PLATFORMS, "plugins", live.plugins());
  }

  @GetMapping("/api/live/rooms")
  public Object rooms() {
    return live.rooms(current.id());
  }

  @PostMapping("/api/live/rooms")
  public Object create(@Valid @RequestBody Room r) {
    return live.create(current.id(), r.name(), r.platform(), r.externalRoom());
  }

  @PutMapping("/api/live/rooms/{id}")
  public void configure(@PathVariable long id, @Valid @RequestBody Settings s) {
    live.configure(current.id(), id, s.status(), s.plugins());
  }

  @PostMapping("/api/live/rooms/{id}/events")
  public Object publish(@PathVariable long id, @Valid @RequestBody Event e) {
    return live.publish(current.id(), id, e.kind(), e.author(), e.content(), e.externalId());
  }

  @GetMapping("/api/live/rooms/{id}/events")
  public Object events(
    @PathVariable long id,
    @RequestParam(defaultValue = "0") @Min(0) long after
  ) {
    live.own(current.id(), id);
    return live.events(id, after);
  }

  @PostMapping("/api/live/rooms/{id}/overlay-token")
  public Object rotate(@PathVariable long id) {
    return Map.of("overlayToken", live.rotate(current.id(), id));
  }

  @GetMapping("/api/live-overlay/{id}")
  public Object overlay(
    @PathVariable long id,
    @RequestParam String token,
    @RequestParam(defaultValue = "0") @Min(0) long after
  ) {
    return org.springframework.http.ResponseEntity.ok()
      .cacheControl(org.springframework.http.CacheControl.noStore())
      .body(live.overlay(id, token, after));
  }
}
