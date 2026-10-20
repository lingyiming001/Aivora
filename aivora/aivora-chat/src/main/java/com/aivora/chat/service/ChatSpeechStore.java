package com.aivora.chat.service;

import com.aivora.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ChatSpeechStore {

  private final JdbcTemplate db;

  public ChatSpeechStore(JdbcTemplate db) {
    this.db = db;
  }

  public Map<String, Object> profile(long chat) {
    db.update(
      "update chat_session s set speech_model_id=m.id,speech_model_name=m.model_name from (select m.id,m.model_name from ai_model m join ai_provider p on p.id=m.provider_id where m.model_type='TTS' and m.enabled and p.enabled and p.api_key_ciphertext is not null and p.base_url='https://dashscope.aliyuncs.com/compatible-mode/v1' order by m.id limit 1) m where s.id=? and s.speech_model_id is null",
      chat
    );
    var rows = db.queryForList(
      "select s.speech_model_name as model_name,m.model_type,p.kind,p.base_url,p.api_key_ciphertext,s.speech_voice,s.speech_language,(m.enabled and p.enabled and m.model_type='TTS' and m.model_name=s.speech_model_name and p.api_key_ciphertext is not null and p.base_url='https://dashscope.aliyuncs.com/compatible-mode/v1') as available from chat_session s join ai_model m on m.id=s.speech_model_id join ai_provider p on p.id=m.provider_id where s.id=?",
      chat
    );
    if (rows.isEmpty()) throw new ApiException(
      HttpStatus.SERVICE_UNAVAILABLE,
      "VOICE_UNAVAILABLE",
      "当前会话的固定音色模型不可用，请管理员恢复配置；不会自动切换声音"
    );
    return rows.getFirst();
  }

  public static void requireAvailable(Map<String, Object> profile) {
    if (!Boolean.TRUE.equals(profile.get("available"))) throw new ApiException(
      HttpStatus.SERVICE_UNAVAILABLE,
      "VOICE_UNAVAILABLE",
      "当前会话的固定音色模型不可用，请管理员恢复配置；不会自动切换声音"
    );
  }

  public record Text(String value, boolean truncated) {}

  public static Text text(String raw) {
    String value = raw
      .replaceAll("!\\[([^]\\n]*)]\\([^\\n)]*\\)", "$1")
      .replaceAll("\\[([^]\\n]+)]\\([^\\n)]*\\)", "$1")
      .replaceAll("(?m)^\\s{0,3}#{1,6}\\s+", "")
      .replace("```", "")
      .replace("**", "")
      .replace("__", "")
      .replace("`", "")
      .trim();
    int limit = Math.min(600, value.length());
    if (
      limit > 0 && limit < value.length() && Character.isHighSurrogate(value.charAt(limit - 1))
    ) limit--;
    return new Text(value.substring(0, limit), value.length() > limit);
  }

  public static String hash(String text, Map<String, Object> profile) {
    try {
      return HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(
          (
            profile.get("model_name") +
            "\n" +
            profile.get("speech_voice") +
            "\n" +
            profile.get("speech_language") +
            "\n" +
            text
          ).getBytes(StandardCharsets.UTF_8)
        )
      );
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  public boolean cached(long message, String hash) {
    return (
      db.queryForObject(
        "select count(*) from chat_speech_audio where message_id=? and text_hash=?",
        Integer.class,
        message,
        hash
      ) > 0
    );
  }

  public void save(long message, String hash, Map<String, Object> profile, byte[] bytes) {
    validateAudio(bytes);
    db.update(
      "insert into chat_speech_audio(message_id,text_hash,model_name,voice,language_type,audio_data) values(?,?,?,?,?,?) on conflict(message_id) do update set text_hash=excluded.text_hash,model_name=excluded.model_name,voice=excluded.voice,language_type=excluded.language_type,audio_data=excluded.audio_data,created_at=now() where chat_speech_audio.text_hash<>excluded.text_hash",
      message,
      hash,
      profile.get("model_name"),
      profile.get("speech_voice"),
      profile.get("speech_language"),
      bytes
    );
  }

  public byte[] audio(long chat, long message) {
    var rows = db.queryForList(
      "select a.audio_data from chat_speech_audio a join chat_message m on m.id=a.message_id join chat_session s on s.id=m.session_id where s.id=? and m.id=? and not m.history_hidden and m.id>s.history_after_id",
      chat,
      message
    );
    if (rows.isEmpty()) throw new ApiException(
      HttpStatus.NOT_FOUND,
      "SPEECH_NOT_FOUND",
      "语音尚未生成或已清空，请重新朗读"
    );
    return (byte[]) rows.getFirst().get("audio_data");
  }

  public static void validateAudio(byte[] bytes) {
    if (
      bytes.length < 44 ||
      bytes.length > 16777216 ||
      !new String(bytes, 0, 4, StandardCharsets.US_ASCII).equals("RIFF") ||
      !new String(bytes, 8, 4, StandardCharsets.US_ASCII).equals("WAVE")
    ) throw new ApiException(
      HttpStatus.BAD_GATEWAY,
      "INVALID_SPEECH_AUDIO",
      "语音服务未返回有效WAV音频，请重试"
    );
  }
}
