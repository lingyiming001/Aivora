package com.aivora.system.service;

import com.aivora.common.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LiveWorkspaceService {

  private final JdbcTemplate db;

  private final List<Map<String, String>> PLUGINS;
  private final Map<String, LiveMessagePlugin> registry;

  @org.springframework.beans.factory.annotation.Autowired
  public LiveWorkspaceService(JdbcTemplate db, List<LiveMessagePlugin> plugins) {
    this.db = db;
    var entries = new LinkedHashMap<String, LiveMessagePlugin>();
    for (var plugin : plugins) {
      if (
        !plugin.id().matches("[a-z][a-z0-9-]{0,29}") ||
        entries.putIfAbsent(plugin.id(), plugin) != null
      ) throw new IllegalArgumentException("插件 ID 无效或重复");
    }
    registry = Map.copyOf(entries);
    PLUGINS = entries
      .values()
      .stream()
      .map(p -> Map.of("id", p.id(), "name", p.name(), "description", p.description()))
      .toList();
  }

  public LiveWorkspaceService(JdbcTemplate db) {
    this(
      db,
      List.of(
        new LivePluginConfiguration().trimLiveMessage(),
        new LivePluginConfiguration().hideLiveLinks()
      )
    );
  }

  public List<Map<String, String>> plugins() {
    return PLUGINS;
  }

  public static final List<Map<String, Object>> PLATFORMS = List.of(
    platform("custom", "通用消息桥接", "bridge"),
    platform("bilibili", "哔哩哔哩", "bridge-required"),
    platform("douyin", "抖音", "bridge-required"),
    platform("twitch", "Twitch", "eventsub"),
    platform("youtube", "YouTube", "livechat"),
    platform("obs", "OBS 字幕", "overlay")
  );

  private static Map<String, Object> platform(String id, String name, String mode) {
    return Map.of("id", id, "name", name, "mode", mode, "connected", false);
  }

  public List<Map<String, Object>> rooms(long user) {
    return db.queryForList(
      "select id,name,platform,external_room,status,plugins,created_at from live_room where user_id=? order by id desc limit 100",
      user
    );
  }

  public void own(long user, long room) {
    if (
      !Boolean.TRUE.equals(
        db.queryForObject(
          "select exists(select 1 from live_room where id=? and user_id=?)",
          Boolean.class,
          room,
          user
        )
      )
    ) throw new ApiException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", "直播间不存在");
  }

  @Transactional
  public Map<String, Object> create(long user, String name, String platform, String external) {
    if (
      PLATFORMS.stream().noneMatch(p -> p.get("id").equals(platform))
    ) throw new IllegalArgumentException("不支持的平台");
    String token = UUID.randomUUID().toString() + UUID.randomUUID();
    Long id = db.queryForObject(
      "insert into live_room(user_id,name,platform,external_room,overlay_hash) values (?,?,?,?,?) returning id",
      Long.class,
      user,
      name,
      platform,
      external,
      hash(token)
    );
    return Map.of("id", id, "overlayToken", token);
  }

  public void configure(long user, long room, String status, List<String> plugins) {
    own(user, room);
    if (!Set.of("READY", "LIVE", "ENDED").contains(status)) throw new IllegalArgumentException(
      "无效直播状态"
    );
    if (
      String.join(",", plugins).length() > 200 ||
      plugins.size() > PLUGINS.size() ||
      plugins.stream().anyMatch(p -> PLUGINS.stream().noneMatch(x -> x.get("id").equals(p)))
    ) throw new IllegalArgumentException("未知插件");
    db.update(
      "update live_room set status=?,plugins=? where id=? and user_id=?",
      status,
      String.join(",", new LinkedHashSet<>(plugins)),
      room,
      user
    );
  }

  @Transactional
  public Map<String, Object> publish(
    long user,
    long room,
    String kind,
    String author,
    String content,
    String externalId
  ) {
    own(user, room);
    if (
      !Set.of("CHAT", "SUBTITLE").contains(kind) ||
      content == null ||
      content.isBlank() ||
      content.length() > 2000 ||
      author == null ||
      author.length() > 80 ||
      (externalId != null && (externalId.isBlank() || externalId.length() > 150))
    ) throw new IllegalArgumentException("消息参数无效");
    // Serialize publish and lifecycle changes to prevent events after a room has ended.
    var settings = db.queryForMap(
      "select status,plugins from live_room where id=? for update",
      room
    );
    if (!"LIVE".equals(settings.get("status"))) throw new ApiException(
      HttpStatus.CONFLICT,
      "ROOM_NOT_LIVE",
      "请先开始直播"
    );
    String text = content;
    for (String plugin : settings.get("plugins").toString().split(",")) {
      var extension = registry.get(plugin);
      if (extension == null) {
        if (plugin.isBlank()) continue;
        throw new ApiException(
          HttpStatus.CONFLICT,
          "PLUGIN_UNAVAILABLE",
          "直播间启用的插件不可用，请重新配置"
        );
      }
      text = extension.process(text);
      if (text == null || text.length() > 2000) throw new ApiException(
        HttpStatus.CONFLICT,
        "PLUGIN_OUTPUT_INVALID",
        "插件输出无效"
      );
    }
    if (text.isBlank()) throw new IllegalArgumentException("消息不能为空");
    var ids = db.queryForList(
      "insert into live_event(room_id,external_id,kind,author,content) values (?,?,?,?,?) on conflict(room_id,external_id) do nothing returning id",
      Long.class,
      room,
      externalId,
      kind,
      author,
      text
    );
    if (ids.isEmpty()) return Map.of("duplicate", true);
    return Map.of("id", ids.getFirst(), "duplicate", false, "content", text);
  }

  public static String apply(String plugin, String text) {
    return switch (plugin) {
      case "trim" -> text.strip().replaceAll("\\s+", " ");
      case "hide-links" -> text.replaceAll("(?i)https?://[^\\s]+", "[链接]");
      default -> text;
    };
  }

  public List<Map<String, Object>> events(long room, long after) {
    return db.queryForList(
      "select id,kind,author,content,created_at from live_event where room_id=? and id>? order by id limit 100",
      room,
      after
    );
  }

  public List<Map<String, Object>> overlay(long room, String token, long after) {
    if (
      token == null ||
      token.length() > 100 ||
      !Boolean.TRUE.equals(
        db.queryForObject(
          "select exists(select 1 from live_room where id=? and overlay_hash=? and status='LIVE')",
          Boolean.class,
          room,
          hash(token)
        )
      )
    ) throw new ApiException(HttpStatus.NOT_FOUND, "OVERLAY_UNAVAILABLE", "字幕未开启或链接无效");
    if (after == 0) return db.queryForList(
      "select * from (select id,kind,author,content,created_at from live_event where room_id=? order by id desc limit 100) recent order by id",
      room
    );
    return events(room, after);
  }

  public String rotate(long user, long room) {
    own(user, room);
    String token = UUID.randomUUID().toString() + UUID.randomUUID();
    db.update("update live_room set overlay_hash=? where id=?", hash(token), room);
    return token;
  }

  private static String hash(String text) {
    try {
      return HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))
      );
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
