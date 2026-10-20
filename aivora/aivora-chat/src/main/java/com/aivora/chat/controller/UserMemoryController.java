package com.aivora.chat.controller;

import com.aivora.chat.service.UserMemoryService;
import com.aivora.common.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user/memories")
public class UserMemoryController {

  private final CurrentUser current;
  private final UserMemoryService memory;
  private final JdbcTemplate db;

  public UserMemoryController(CurrentUser current, UserMemoryService memory, JdbcTemplate db) {
    this.current = current;
    this.memory = memory;
    this.db = db;
  }

  private long user() {
    long id = current.id();
    if (current.guest()) throw new ApiException(
      HttpStatus.FORBIDDEN,
      "LOGIN_REQUIRED",
      "登录后使用长期记忆"
    );
    return id;
  }

  @GetMapping
  public Object list() {
    long id = user();
    return Map.of("enabled", memory.enabled(id), "items", memory.list(id));
  }

  public record Setting(@NotNull Boolean enabled) {}

  @PutMapping
  public void setting(@Valid @RequestBody Setting req) {
    db.update("update app_user set memory_enabled=? where id=?", req.enabled(), user());
  }

  public record Entry(@NotBlank @Size(max = 300) String content) {}

  @PutMapping("/{key}")
  public void save(@PathVariable String key, @Valid @RequestBody Entry req) {
    long id = user();
    if (!UserMemoryService.KEYS.contains(key)) throw new ApiException(
      HttpStatus.BAD_REQUEST,
      "INVALID_MEMORY_KEY",
      "不支持的偏好类型"
    );
    memory.save(id, key, req.content().trim(), "USER");
  }

  @DeleteMapping("/{key}")
  public void delete(@PathVariable String key) {
    db.update("delete from user_memory where user_id=? and memory_key=?", user(), key);
  }
}
