package com.aivora.system.controller;

import com.aivora.common.CurrentUser;
import com.aivora.system.service.LiveWorkspaceService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Stateless JSON response transport, MCP 2025-11-25 compatibility endpoint. */
@RestController
@RequestMapping("/api/mcp")
public class LiveMcpController {

  private final LiveWorkspaceService live;
  private final CurrentUser current;

  public LiveMcpController(LiveWorkspaceService live, CurrentUser current) {
    this.live = live;
    this.current = current;
  }

  @PostMapping(produces = "application/json")
  public ResponseEntity<?> http(
    @RequestBody JsonNode req,
    @RequestHeader(value = "MCP-Protocol-Version", required = false) String version
  ) {
    if (
      version != null && !Set.of("2025-03-26", "2025-06-18", "2025-11-25").contains(version)
    ) return ResponseEntity.badRequest().body(Map.of("error", "Unsupported MCP protocol version"));
    return rpc(req);
  }

  public ResponseEntity<?> rpc(JsonNode req) {
    Object id = req.has("id") ? req.get("id") : null;
    if (
      !req.isObject() ||
      !"2.0".equals(req.path("jsonrpc").asText()) ||
      !req.path("method").isTextual()
    ) return error(id, -32600, "Invalid Request");
    String method = req.path("method").asText();
    if (!req.has("id")) return ResponseEntity.accepted().build();
    if (!(req.get("id").isTextual() || req.get("id").isNumber())) return error(
      null,
      -32600,
      "Invalid id"
    );
    try {
      Object result = switch (method) {
        case "initialize" -> Map.of(
          "protocolVersion",
          "2025-11-25",
          "capabilities",
          Map.of("tools", Map.of()),
          "serverInfo",
          Map.of("name", "aivora-live", "version", "0.1.0")
        );
        case "ping" -> Map.of();
        case "tools/list" -> Map.of(
          "tools",
          List.of(
            tool("live_rooms", "列出当前账号的直播间", Map.of(), List.of()),
            tool("live_catalog", "查看平台和内置插件", Map.of(), List.of()),
            tool(
              "live_events",
              "读取自己直播间的消息",
              Map.of(
                "roomId",
                Map.of("type", "integer", "minimum", 1),
                "after",
                Map.of("type", "integer", "minimum", 0)
              ),
              List.of("roomId")
            ),
            tool(
              "live_publish",
              "向正在直播的自有直播间发送弹幕或字幕",
              Map.of(
                "roomId",
                Map.of("type", "integer", "minimum", 1),
                "kind",
                Map.of("type", "string", "enum", List.of("CHAT", "SUBTITLE")),
                "author",
                Map.of("type", "string", "maxLength", 80),
                "content",
                Map.of("type", "string", "minLength", 1, "maxLength", 2000),
                "externalId",
                Map.of("type", "string", "minLength", 1, "maxLength", 150)
              ),
              List.of("roomId", "kind", "author", "content")
            )
          )
        );
        case "tools/call" -> call(req.path("params"));
        default -> null;
      };
      if (result == null) return error(id, -32601, "Method not found");
      return ResponseEntity.ok(Map.of("jsonrpc", "2.0", "id", id, "result", result));
    } catch (IllegalArgumentException e) {
      return error(id, -32602, "参数无效");
    }
  }

  private Object call(JsonNode params) {
    String name = params.path("name").asText();
    if (
      !Set.of("live_rooms", "live_catalog", "live_events", "live_publish").contains(name)
    ) throw new IllegalArgumentException();
    JsonNode a = params.path("arguments");
    try {
      Object data = switch (name) {
        case "live_rooms" -> live.rooms(current.id());
        case "live_catalog" -> Map.of("platforms", live.PLATFORMS, "plugins", live.plugins());
        case "live_events" -> {
          long room = room(a);
          long after = a.path("after").asLong(0);
          if (after < 0) throw new IllegalArgumentException();
          live.own(current.id(), room);
          yield live.events(room, after);
        }
        case "live_publish" -> live.publish(
          current.id(),
          room(a),
          str(a, "kind"),
          str(a, "author"),
          str(a, "content"),
          a.has("externalId") ? str(a, "externalId") : null
        );
        default -> throw new IllegalArgumentException();
      };
      String json = new com.fasterxml.jackson.databind.ObjectMapper()
        .findAndRegisterModules()
        .writeValueAsString(data);
      return Map.of("content", List.of(Map.of("type", "text", "text", json)), "isError", false);
    } catch (com.aivora.common.ApiException e) {
      return Map.of(
        "content",
        List.of(Map.of("type", "text", "text", e.getMessage())),
        "isError",
        true
      );
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  private static long room(JsonNode a) {
    if (
      !a.path("roomId").isIntegralNumber() ||
      !a.path("roomId").canConvertToLong() ||
      a.path("roomId").asLong() < 1
    ) throw new IllegalArgumentException();
    return a.path("roomId").asLong();
  }

  private static String str(JsonNode a, String key) {
    if (!a.path(key).isTextual()) throw new IllegalArgumentException();
    return a.path(key).asText();
  }

  private static Object tool(
    String name,
    String description,
    Map<String, Object> props,
    List<String> required
  ) {
    return Map.of(
      "name",
      name,
      "description",
      description,
      "inputSchema",
      Map.of(
        "type",
        "object",
        "properties",
        props,
        "required",
        required,
        "additionalProperties",
        false
      )
    );
  }

  private static ResponseEntity<?> error(Object id, int code, String message) {
    var r = new LinkedHashMap<String, Object>();
    r.put("jsonrpc", "2.0");
    r.put("id", id);
    r.put("error", Map.of("code", code, "message", message));
    return ResponseEntity.ok(r);
  }

  @GetMapping
  public ResponseEntity<?> stream() {
    return ResponseEntity.status(405).build();
  }

  @DeleteMapping
  public ResponseEntity<?> delete() {
    return ResponseEntity.status(405).build();
  }
}
