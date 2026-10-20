package com.aivora.system.controller;

import com.aivora.common.CurrentUser;
import com.aivora.system.service.LiveConnectionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/live/rooms/{id}/connection")
public class LiveConnectionController {

  private final LiveConnectionService connections;
  private final CurrentUser current;

  public LiveConnectionController(LiveConnectionService connections, CurrentUser current) {
    this.connections = connections;
    this.current = current;
  }

  public record Config(
    @NotBlank @Size(max = 200) String sourceId,
    @NotNull @Size(max = 100) String clientId,
    @NotNull @Size(max = 100) String actorId,
    @NotNull @Size(max = 4096) String token
  ) {}

  @GetMapping
  public Object status(@PathVariable long id) {
    return connections.status(current.id(), id);
  }

  @PutMapping
  public void save(@PathVariable long id, @Valid @RequestBody Config c) {
    connections.save(current.id(), id, c.sourceId(), c.clientId(), c.actorId(), c.token());
  }

  @PostMapping("/connect")
  public void connect(@PathVariable long id) {
    connections.connect(current.id(), id);
  }

  @PostMapping("/disconnect")
  public void disconnect(@PathVariable long id) {
    connections.disconnect(current.id(), id);
  }
}
