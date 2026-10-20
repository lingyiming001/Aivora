package com.aivora.system.service;

import com.aivora.ai.infrastructure.SecretCipher;
import com.aivora.common.*;
import jakarta.annotation.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class LiveConnectionService {

  private final JdbcTemplate db;
  private final SecretCipher cipher;
  private final LiveWorkspaceService live;
  private final LivePlatformClient platforms;
  private final Map<Long, Worker> workers = new ConcurrentHashMap<>();
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
    r -> {
      var t = new Thread(r, "live-connections");
      t.setDaemon(true);
      return t;
    }
  );
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  public LiveConnectionService(
    JdbcTemplate db,
    SecretCipher cipher,
    LiveWorkspaceService live,
    LivePlatformClient platforms
  ) {
    this.db = db;
    this.cipher = cipher;
    this.live = live;
    this.platforms = platforms;
  }

  @PostConstruct
  public void start() {
    scheduler.scheduleWithFixedDelay(this::tick, 2, 3, TimeUnit.SECONDS);
  }

  public Object status(long user, long room) {
    live.own(user, room);
    var rows = db.queryForList(
      "select source_id,client_id,actor_id,enabled,status,detail,updated_at,true as has_token from live_connection where room_id=?",
      room
    );
    return rows.isEmpty()
      ? Map.of("status", "UNCONFIGURED", "enabled", false, "has_token", false)
      : rows.getFirst();
  }

  public synchronized void save(
    long user,
    long room,
    String source,
    String client,
    String actor,
    String token
  ) {
    live.own(user, room);
    String platform = db.queryForObject(
      "select platform from live_room where id=?",
      String.class,
      room
    );
    if (!Set.of("youtube", "twitch").contains(platform)) throw input("此平台尚未提供自动连接器");
    if (
      source.isBlank() ||
      source.length() > 200 ||
      client.length() > 100 ||
      actor.length() > 100 ||
      token.length() > 4096
    ) throw input("平台配置无效");
    if (
      platform.equals("twitch") &&
      (!source.matches("[0-9]+") || !actor.matches("[0-9]+") || !client.matches("[a-zA-Z0-9]+"))
    ) throw input("Twitch 需要主播用户 ID、应用 Client ID 和授权用户 ID");
    var old = db.queryForList("select token_ciphertext from live_connection where room_id=?", room);
    String encrypted = token.isBlank()
      ? old.isEmpty()
        ? null
        : (String) old.getFirst().get("token_ciphertext")
      : cipher.encrypt(token.strip());
    if (encrypted == null) throw input("请填写平台访问令牌");
    String revision = UUID.randomUUID().toString();
    db.update(
      "insert into live_connection(room_id,source_id,client_id,actor_id,token_ciphertext,revision) values (?,?,?,?,?,?) on conflict(room_id) do update set source_id=excluded.source_id,client_id=excluded.client_id,actor_id=excluded.actor_id,token_ciphertext=excluded.token_ciphertext,revision=excluded.revision,enabled=false,status='DISCONNECTED',detail='',updated_at=now()",
      room,
      source.strip(),
      client.strip(),
      actor.strip(),
      encrypted,
      revision
    );
    remove(room);
  }

  public synchronized void connect(long user, long room) {
    live.own(user, room);
    if (
      !"LIVE".equals(
        db.queryForObject("select status from live_room where id=?", String.class, room)
      )
    ) throw input("请先开始互动");
    Integer count = db.queryForObject(
      "select count(*) from live_connection where enabled and room_id<>?",
      Integer.class,
      room
    );
    if (count != null && count >= 64) throw input("平台连接数已达上限，请断开其他房间后重试");
    remove(room);
    if (
      db.update(
        "update live_connection set enabled=true,revision=?,status='CONNECTING',detail='',updated_at=now() where room_id=?",
        UUID.randomUUID().toString(),
        room
      ) == 0
    ) throw input("请先保存平台配置");
  }

  public synchronized void disconnect(long user, long room) {
    live.own(user, room);
    db.update(
      "update live_connection set enabled=false,revision=?,status='DISCONNECTED',detail='',updated_at=now() where room_id=?",
      UUID.randomUUID().toString(),
      room
    );
    remove(room);
  }

  private void remove(long room) {
    var worker = workers.remove(room);
    if (worker != null) worker.close();
  }

  synchronized void tick() {
    try {
      var configs = db.queryForList(
        "select c.*,r.user_id,r.platform from live_connection c join live_room r on r.id=c.room_id join app_user u on u.id=r.user_id where c.enabled and r.status='LIVE' and u.status='ACTIVE' and c.status<>'ERROR' order by c.room_id limit 64"
      );
      var valid = new HashSet<Long>();
      for (var c : configs) {
        long room = ((Number) c.get("room_id")).longValue();
        valid.add(room);
        var current = workers.get(room);
        if (current != null && !current.revision.equals(c.get("revision"))) {
          remove(room);
          current = null;
        }
        if (current == null) {
          var worker = new Worker(c);
          workers.put(room, worker);
          worker.future = executor.submit(worker::run);
        }
      }
      for (long id : List.copyOf(workers.keySet())) if (!valid.contains(id)) remove(id);
      db.update(
        "update live_connection c set enabled=false,status='DISCONNECTED',detail='',updated_at=now() from live_room r join app_user u on u.id=r.user_id where c.room_id=r.id and c.enabled and (r.status<>'LIVE' or u.status<>'ACTIVE')"
      );
    } catch (RuntimeException unavailable) {
      /* DB outages are retried on the next tick. */
    }
  }

  private final class Worker {

    final long room, user;
    final String revision, platform, source, client, actor, encrypted;
    volatile boolean closed;
    volatile AutoCloseable socket;
    volatile Future<?> future;
    volatile String state = "CONNECTING";

    Worker(Map<String, Object> c) {
      room = ((Number) c.get("room_id")).longValue();
      user = ((Number) c.get("user_id")).longValue();
      revision = (String) c.get("revision");
      platform = (String) c.get("platform");
      source = (String) c.get("source_id");
      client = (String) c.get("client_id");
      actor = (String) c.get("actor_id");
      encrypted = (String) c.get("token_ciphertext");
    }

    boolean active() {
      return (
        !closed &&
        Boolean.TRUE.equals(
          db.queryForObject(
            "select exists(select 1 from live_connection c join live_room r on r.id=c.room_id join app_user u on u.id=r.user_id where c.room_id=? and c.revision=? and c.enabled and r.status='LIVE' and u.status='ACTIVE')",
            Boolean.class,
            room,
            revision
          )
        )
      );
    }

    void state(String value) {
      if (closed) return;
      state = value;
      String detail = switch (value) {
        case "ERROR" -> "平台授权失效、权限不足或配置无效，请检查后重新连接";
        case "RETRYING" -> "连接中断，正在重试";
        default -> "";
      };
      db.update(
        "update live_connection set status=?,detail=?,updated_at=now() where room_id=? and revision=? and enabled",
        value,
        detail,
        room,
        revision
      );
    }

    void accept(LivePlatformClient.Message m) {
      if (!active() || m.id().isBlank() || m.content().isBlank()) return;
      String id =
        platform +
        ":" +
        UUID.nameUUIDFromBytes(m.id().getBytes(java.nio.charset.StandardCharsets.UTF_8));
      live.publish(user, room, "CHAT", shorten(m.author(), 80), shorten(m.content(), 2000), id);
    }

    void run() {
      String cursor = "";
      long backoff = 2000;
      while (!closed) {
        try {
          if (!active()) return;
          state("CONNECTING");
          String token = cipher.decrypt(encrypted);
          if ("youtube".equals(platform)) {
            var page = platforms.youtube(source, token, cursor);
            for (var m : page.messages()) accept(m);
            cursor = page.cursor();
            state("CONNECTED");
            backoff = 2000;
            if (page.ended()) {
              state("ERROR");
              return;
            }
            Thread.sleep(page.delay());
          } else {
            socket = platforms.twitch(source, client, actor, token, this::accept, this::state);
            while (!closed && !Set.of("ERROR", "RETRYING").contains(state)) {
              Thread.sleep(2000);
              if (!active()) return;
            }
            if (socket != null) socket.close();
            socket = null;
            if ("ERROR".equals(state)) return;
            throw new IllegalStateException("Twitch connection lost");
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return;
        } catch (LivePlatformClient.PlatformFailure e) {
          if (e.status == 401 || e.status == 403 || e.status == 400 || e.status == 404) {
            state("ERROR");
            return;
          }
          state("RETRYING");
        } catch (Exception e) {
          if (closed) return;
          try {
            state("RETRYING");
          } catch (RuntimeException ignored) {}
        }
        if ("RETRYING".equals(state)) {
          try {
            Thread.sleep(backoff);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
          }
          backoff = Math.min(backoff * 2, 60000);
        }
      }
    }

    void close() {
      closed = true;
      try {
        if (socket != null) socket.close();
      } catch (Exception ignored) {}
      if (future != null) future.cancel(true);
    }
  }

  public static String shorten(String text, int length) {
    return text.length() <= length ? text : text.substring(0, length);
  }

  private static ApiException input(String message) {
    return new ApiException(HttpStatus.BAD_REQUEST, "LIVE_CONNECTION_INVALID", message);
  }

  @PreDestroy
  public synchronized void close() {
    scheduler.shutdownNow();
    workers.values().forEach(Worker::close);
    workers.clear();
    executor.shutdownNow();
  }
}
