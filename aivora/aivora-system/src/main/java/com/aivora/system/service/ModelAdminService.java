package com.aivora.system.service;

import com.aivora.ai.infrastructure.SecretCipher;
import com.aivora.chat.service.CompositionService;
import com.aivora.common.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModelAdminService {

  private final JdbcTemplate db;
  private final SecretCipher cipher;
  private final ObjectMapper json;
  private final HttpClient http = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(5))
    .build();

  public ModelAdminService(JdbcTemplate db, SecretCipher cipher, ObjectMapper json) {
    this.db = db;
    this.cipher = cipher;
    this.json = json;
  }

  public Object providers() {
    return db.queryForList(
      "select id,name,kind,base_url,enabled,(api_key_ciphertext is not null) as key_configured from ai_provider order by id"
    );
  }

  public Object models() {
    var rows = db.queryForList(
      "select m.*,p.name as provider,p.enabled as provider_enabled,p.base_url,p.kind from ai_model m join ai_provider p on p.id=m.provider_id order by p.id,m.id"
    );
    for (var row : rows) {
      try {
        CompositionService.requireApprovedProvider(row);
        row.put("composition_allowed", true);
      } catch (ApiException e) {
        row.put("composition_allowed", false);
      }
    }
    return rows;
  }

  public static String validateUrl(String value) {
    try {
      URI u = URI.create(value.trim());
      if (
        !List.of("https", "http").contains(u.getScheme()) ||
        u.getHost() == null ||
        u.getUserInfo() != null ||
        u.getQuery() != null ||
        u.getFragment() != null
      ) throw new IllegalArgumentException();
      return value.trim().replaceAll("/+$", "");
    } catch (Exception e) {
      throw new ApiException(
        HttpStatus.BAD_REQUEST,
        "INVALID_URL",
        "请输入有效的 HTTP(S) Base URL，不含密钥或查询参数"
      );
    }
  }

  @Transactional
  public long createProvider(
    String name,
    String kind,
    String baseUrl,
    String apiKey,
    boolean enabled,
    long actor
  ) {
    db.queryForList("select pg_advisory_xact_lock(801237)");
    if (!"OPENAI_COMPATIBLE".equals(kind)) throw new ApiException(
      HttpStatus.BAD_REQUEST,
      "INVALID_PROVIDER_KIND",
      "请选择 OpenAI 兼容协议"
    );
    String cleanName = providerName(name, null);
    String key = apiKey == null || apiKey.isBlank() ? null : cipher.encrypt(apiKey.trim());
    if (enabled && key == null) throw new ApiException(
      HttpStatus.BAD_REQUEST,
      "KEY_REQUIRED",
      "请先填写 API Key 再启用供应商"
    );
    Long id = db.queryForObject(
      "insert into ai_provider(name,kind,base_url,api_key_ciphertext,enabled) values (?,?,?,?,?) returning id",
      Long.class,
      cleanName,
      kind,
      validateUrl(baseUrl),
      key,
      enabled
    );
    audit(actor, "PROVIDER_CREATE", "provider:" + id);
    return id;
  }

  private String providerName(String name, Long id) {
    if (name == null || name.isBlank() || name.trim().length() > 80) throw new ApiException(
      HttpStatus.BAD_REQUEST,
      "INVALID_PROVIDER_NAME",
      "供应商名称不能为空且不得超过80个字符"
    );
    if (
      db.queryForObject(
        "select count(*) from ai_provider where lower(name)=lower(?) and (cast(? as bigint) is null or id<>?)",
        Integer.class,
        name.trim(),
        id,
        id
      ) > 0
    ) throw new ApiException(HttpStatus.CONFLICT, "PROVIDER_NAME_EXISTS", "供应商名称已存在");
    return name.trim();
  }

  @Transactional
  public void provider(
    long id,
    String baseUrl,
    String apiKey,
    boolean enabled,
    boolean clearKey,
    String name,
    long actor
  ) {
    db.queryForList("select pg_advisory_xact_lock(801237)");
    provider(id, baseUrl, apiKey, enabled, clearKey, actor);
    if (name != null) db.update(
      "update ai_provider set name=? where id=?",
      providerName(name, id),
      id
    );
  }

  @Transactional
  public void provider(
    long id,
    String baseUrl,
    String apiKey,
    boolean enabled,
    boolean clearKey,
    long actor
  ) {
    db.queryForList("select pg_advisory_xact_lock(801237)");
    var rows = db.queryForList(
      "select api_key_ciphertext,kind from ai_provider where id=? for update",
      id
    );
    if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "供应商不存在");
    String key = (String) rows.getFirst().get("api_key_ciphertext");
    if (clearKey) key = null;
    else if (apiKey != null && !apiKey.isBlank()) key = cipher.encrypt(apiKey.trim());
    if (enabled && "LOCAL_DEMO".equals(rows.getFirst().get("kind"))) throw new ApiException(
      HttpStatus.BAD_REQUEST,
      "DEMO_DISABLED",
      "演示模型已停用，请配置真实供应商"
    );
    if (
      enabled && key == null && !"LOCAL_DEMO".equals(rows.getFirst().get("kind"))
    ) throw new ApiException(
      HttpStatus.BAD_REQUEST,
      "KEY_REQUIRED",
      "请先填写 API Key 再启用供应商"
    );
    db.update(
      "update ai_provider set base_url=?,api_key_ciphertext=?,enabled=? where id=?",
      validateUrl(baseUrl),
      key,
      enabled,
      id
    );
    if (enabled) db.update(
      "update ai_model set is_default=true where id=(select id from ai_model where provider_id=? and enabled and model_type in ('CHAT','TEXT') order by id limit 1) and not exists(select 1 from ai_model where is_default)",
      id
    );
    if (!enabled) db.update("update ai_model set is_default=false where provider_id=?", id);
    audit(actor, "PROVIDER_UPDATE", "provider:" + id);
  }

  @Transactional
  public void model(
    Long id,
    long provider,
    String name,
    long stars,
    boolean enabled,
    boolean isDefault,
    long actor
  ) {
    model(id, provider, name, stars, enabled, isDefault, "CHAT", actor);
  }

  @Transactional
  public void model(
    Long id,
    long provider,
    String name,
    long stars,
    boolean enabled,
    boolean isDefault,
    String type,
    long actor
  ) {
    if (type == null) type = "CHAT";
    if (
      !List.of("TEXT", "CHAT", "VOICE", "ASR", "TTS", "IMAGE", "MULTIMODAL").contains(type)
    ) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MODEL_TYPE", "模型类型无效");
    if (isDefault && !List.of("TEXT", "CHAT").contains(type)) throw new ApiException(
      HttpStatus.BAD_REQUEST,
      "INVALID_MODEL_TYPE",
      "非文字模型不能设为文字聊天兜底模型"
    );
    db.queryForList("select pg_advisory_xact_lock(801237)");
    if (
      !List.of("TEXT", "CHAT").contains(type) &&
      db.queryForObject(
        "select count(*) from model_route_rule r join ai_model m on (r.model_id=m.id or m.id=any(r.helper_model_ids)) where m.id=? or (m.provider_id=? and m.model_name=?)",
        Integer.class,
        id,
        provider,
        name.trim()
      ) > 0
    ) throw new ApiException(
      HttpStatus.CONFLICT,
      "MODEL_IN_ROUTE",
      "请先移除使用此模型的聊天路由，再改为媒体类型"
    );
    if (isDefault) {
      if (
        !enabled ||
        !Boolean.TRUE.equals(
          db.queryForObject("select enabled from ai_provider where id=?", Boolean.class, provider)
        )
      ) throw new ApiException(
        HttpStatus.BAD_REQUEST,
        "UNAVAILABLE",
        "默认路由必须指向已启用的供应商和模型"
      );
      db.update("update ai_model set is_default=false where is_default");
    }
    if (id == null) db.update(
      "insert into ai_model(provider_id,model_name,star_per_request,enabled,is_default,model_type) values (?,?,?,?,?,?) on conflict(provider_id,model_name) do update set star_per_request=excluded.star_per_request,enabled=excluded.enabled,is_default=excluded.is_default,model_type=excluded.model_type",
      provider,
      name.trim(),
      stars,
      enabled,
      isDefault,
      type
    );
    else if (
      db.update(
        "update ai_model set model_name=?,star_per_request=?,enabled=?,is_default=?,model_type=? where id=? and provider_id=?",
        name.trim(),
        stars,
        enabled,
        isDefault,
        type,
        id,
        provider
      ) != 1
    ) throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "模型不存在");
    audit(actor, "MODEL_UPDATE", "provider:" + provider + " model:" + name.trim());
  }

  public Object probe(long id) {
    var rows = db.queryForList(
      "select m.model_type,m.model_name,p.kind,p.base_url,p.api_key_ciphertext from ai_model m join ai_provider p on p.id=m.provider_id where m.id=?",
      id
    );
    if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "模型不存在");
    var m = rows.getFirst();
    if ("LOCAL_DEMO".equals(m.get("kind"))) return Map.of(
      "ok",
      true,
      "message",
      "内置开发演示模型，无需外部服务"
    );
    if (!List.of("TEXT", "CHAT").contains(m.get("model_type"))) throw new ApiException(
      HttpStatus.BAD_REQUEST,
      "VOICE_TEST_UNSUPPORTED",
      "媒体模型请通过对应媒体接口测试；文字连接测试不适用"
    );
    String key = cipher.decrypt((String) m.get("api_key_ciphertext"));
    if (key == null) throw new ApiException(
      HttpStatus.BAD_REQUEST,
      "KEY_REQUIRED",
      "请先保存供应商 API Key"
    );
    long start = System.nanoTime();
    try {
      String body = json.writeValueAsString(
        Map.of(
          "model",
          m.get("model_name"),
          "messages",
          List.of(Map.of("role", "user", "content", "Reply OK")),
          "stream",
          false,
          "max_tokens",
          16
        )
      );
      var request = HttpRequest.newBuilder(
        URI.create(validateUrl((String) m.get("base_url")) + "/chat/completions")
      )
        .timeout(Duration.ofSeconds(20))
        .header("Content-Type", "application/json")
        .header("Authorization", "Bearer " + key)
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build();
      var response = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() / 100 != 2) return Map.of(
        "ok",
        false,
        "message",
        "供应商返回 HTTP " + response.statusCode() + "，请检查密钥、模型权限和额度"
      );
      if (!json.readTree(response.body()).path("choices").isArray()) return Map.of(
        "ok",
        false,
        "message",
        "响应格式不兼容 Chat Completions"
      );
      return Map.of(
        "ok",
        true,
        "message",
        "模型调用成功",
        "latencyMs",
        (System.nanoTime() - start) / 1_000_000
      );
    } catch (Exception e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      return Map.of("ok", false, "message", "连接失败或超时，请检查 Base URL 和网络");
    }
  }

  private void audit(long actor, String action, String subject) {
    db.update(
      "insert into audit_log(actor_id,action,subject) values (?,?,?)",
      actor,
      action,
      subject
    );
  }
}
