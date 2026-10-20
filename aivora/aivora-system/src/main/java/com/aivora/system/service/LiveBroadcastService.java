package com.aivora.system.service;

import com.aivora.common.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class LiveBroadcastService {

  private final JdbcTemplate db;
  private final LiveWorkspaceService live;
  private final boolean enabled;
  private final String publishBase, internalBase;
  private final HttpClient http = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(5))
    .build();

  public LiveBroadcastService(
    JdbcTemplate db,
    LiveWorkspaceService live,
    @Value("${aivora.live.media-enabled:false}") boolean enabled,
    @Value("${aivora.live.rtmp-public-url:rtmp://localhost:1935/live}") String publishBase,
    @Value("${aivora.live.hls-internal-url:http://127.0.0.1:18085}") String internalBase
  ) {
    this.db = db;
    this.live = live;
    this.enabled = enabled;
    this.publishBase = publishBase.replaceAll("/+$", "");
    this.internalBase = internalBase.replaceAll("/+$", "");
  }

  public Object config(long user, long room) {
    live.own(user, room);
    var rows = db.queryForList("select stream_id from live_broadcast where room_id=?", room);
    var result = new LinkedHashMap<String, Object>();
    result.put("enabled", enabled);
    result.put("rtmpUrl", publishBase);
    if (!rows.isEmpty()) result.put("streamId", rows.getFirst().get("stream_id").toString());
    return result;
  }

  public Object rotate(long user, long room) {
    live.own(user, room);
    if (!enabled) throw new ApiException(
      HttpStatus.SERVICE_UNAVAILABLE,
      "MEDIA_SERVER_DISABLED",
      "请先部署并启用媒体服务"
    );
    UUID stream = UUID.randomUUID();
    String token = UUID.randomUUID().toString() + UUID.randomUUID();
    db.update(
      "insert into live_broadcast(room_id,stream_id,publish_hash) values (?,?,?) on conflict(room_id) do update set stream_id=excluded.stream_id,publish_hash=excluded.publish_hash,created_at=now()",
      room,
      stream,
      hash(token)
    );
    return Map.of(
      "rtmpUrl",
      publishBase,
      "streamId",
      stream.toString(),
      "streamKey",
      stream + "?token=" + token
    );
  }

  public boolean canView(String stream) {
    try {
      if (!enabled) return false;
      UUID id = UUID.fromString(stream);
      return Boolean.TRUE.equals(
        db.queryForObject(
          "select exists(select 1 from live_broadcast b join live_room r on r.id=b.room_id join app_user u on u.id=r.user_id where b.stream_id=? and r.status='LIVE' and u.status='ACTIVE')",
          Boolean.class,
          id
        )
      );
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  public boolean allowPublish(String action, String app, String stream, String params) {
    if (!"on_publish".equals(action) || !"live".equals(app) || !canView(stream)) return false;
    try {
      String token = null;
      for (String entry : (params.startsWith("?") ? params.substring(1) : params).split("&")) {
        String[] kv = entry.split("=", 2);
        if (kv.length == 2 && URLDecoder.decode(kv[0], StandardCharsets.UTF_8).equals("token")) {
          if (token != null) return false;
          token = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
        }
      }
      if (token == null || token.length() > 100) return false;
      return Boolean.TRUE.equals(
        db.queryForObject(
          "select exists(select 1 from live_broadcast where stream_id=? and publish_hash=?)",
          Boolean.class,
          UUID.fromString(stream),
          hash(token)
        )
      );
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  public byte[] segment(String stream, String file) {
    if (!canView(stream) || !validFile(stream, file)) throw new ApiException(
      HttpStatus.NOT_FOUND,
      "STREAM_UNAVAILABLE",
      "直播尚未开始或链接已失效"
    );
    String path = file.equals("index.m3u8") ? stream + ".m3u8" : file;
    try {
      var request = HttpRequest.newBuilder(URI.create(internalBase + "/live/" + path))
        .timeout(Duration.ofSeconds(10))
        .GET()
        .build();
      var response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
      try (var body = response.body()) {
        if (response.statusCode() != 200) throw new ApiException(
          HttpStatus.NOT_FOUND,
          "STREAM_NOT_READY",
          "尚未收到视频，请检查 OBS 推流状态"
        );
        byte[] data = body.readNBytes(8 * 1024 * 1024 + 1);
        if (data.length > 8 * 1024 * 1024) throw new IllegalStateException(
          "Oversized media segment"
        );
        return data;
      }
    } catch (ApiException e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw unavailable();
    } catch (Exception e) {
      throw unavailable();
    }
  }

  public static boolean validFile(String stream, String file) {
    return (
      file.equals("index.m3u8") ||
      (file.startsWith(stream + "-") &&
        file.endsWith(".ts") &&
        file.substring(stream.length() + 1, file.length() - 3).matches("[0-9]+"))
    );
  }

  private static ApiException unavailable() {
    return new ApiException(
      HttpStatus.BAD_GATEWAY,
      "MEDIA_SERVER_UNAVAILABLE",
      "媒体服务不可用，请检查媒体服务部署"
    );
  }

  private static String hash(String text) {
    try {
      return HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))
      );
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
