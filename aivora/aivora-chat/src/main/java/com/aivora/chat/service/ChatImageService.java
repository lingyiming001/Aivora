package com.aivora.chat.service;

import com.aivora.common.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import javax.imageio.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ChatImageService {

  private final JdbcTemplate db;
  private final HttpClient http = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(10))
    .followRedirects(HttpClient.Redirect.NEVER)
    .build();

  public ChatImageService(JdbcTemplate db) {
    this.db = db;
  }

  public String validate(String image) {
    if (image == null || !image.startsWith("data:image/")) throw new ApiException(
      HttpStatus.BAD_REQUEST,
      "INVALID_CHAT_IMAGE",
      "请上传有效PNG或JPEG图片"
    );
    return com.aivora.common.util.AvatarValidator.validateAvatar(image);
  }

  public Map<String, Object> store(
    long user,
    long chat,
    String image,
    String description,
    String source
  ) {
    UUID id = UUID.randomUUID();
    db.update(
      "insert into chat_image_attachment(id,user_id,session_id,image_data,description,source) values (?,?,?,?,?,?)",
      id,
      user,
      chat,
      validate(image),
      description,
      source
    );
    return Map.of("attachmentId", id, "image", image, "text", description, "source", source);
  }

  public Map<String, Object> pending(long user, long chat, UUID id) {
    var rows = db.queryForList(
      "select id,image_data as image,description,source,message_id as used_message_id from chat_image_attachment where id=? and user_id=? and session_id=? for update",
      id,
      user,
      chat
    );
    if (rows.isEmpty()) throw new ApiException(
      HttpStatus.BAD_REQUEST,
      "ATTACHMENT_UNAVAILABLE",
      "图片附件不可用，请重新选择图片"
    );
    return rows.getFirst();
  }

  public void describe(UUID id, String description) {
    db.update("update chat_image_attachment set description=? where id=?", description, id);
  }

  public record GeneratedImage(String preview, byte[] original, String mime) {}

  @org.springframework.transaction.annotation.Transactional
  public Map<String, Object> storeGenerated(long user, long chat, GeneratedImage image) {
    var result = store(user, chat, image.preview(), "", "GENERATED");
    db.update(
      "update chat_image_attachment set original_image=?,original_media_type=? where id=?",
      image.original(),
      image.mime(),
      result.get("attachmentId")
    );
    return result;
  }

  public Map<String, Object> original(long user, long chat, UUID id) {
    var rows = db.queryForList(
      "select a.image_data,a.original_image,a.original_media_type from chat_image_attachment a join chat_session s on s.id=a.session_id where a.id=? and a.user_id=? and a.session_id=? and s.user_id=? and s.deleted_at is null",
      id,
      user,
      chat,
      user
    );
    if (rows.isEmpty()) throw new ApiException(
      HttpStatus.NOT_FOUND,
      "IMAGE_UNAVAILABLE",
      "图片不可用"
    );
    var row = rows.getFirst();
    if (row.get("original_image") == null) return Map.of(
      "bytes",
      Base64.getDecoder().decode(((String) row.get("image_data")).split(",", 2)[1]),
      "mime",
      "image/jpeg"
    );
    return Map.of("bytes", row.get("original_image"), "mime", row.get("original_media_type"));
  }

  public String editingImage(long user, long chat, UUID id) {
    var image = original(user, chat, id);
    byte[] bytes = (byte[]) image.get("bytes");
    if (bytes.length <= 10 * 1024 * 1024) return (
      "data:" + image.get("mime") + ";base64," + Base64.getEncoder().encodeToString(bytes)
    );
    return (String) pending(user, chat, id).get("image");
  }

  public String download(String url) {
    return downloadGenerated(url).preview();
  }

  public GeneratedImage downloadGenerated(String url) {
    try {
      var response = http.send(
        HttpRequest.newBuilder(URI.create(ChatMediaService.safeMediaUrl(url)))
          .timeout(Duration.ofSeconds(30))
          .GET()
          .build(),
        HttpResponse.BodyHandlers.ofInputStream()
      );
      try (var body = response.body()) {
        if (response.statusCode() / 100 != 2) throw new IOException("下载图片失败");
        byte[] bytes = body.readNBytes(12 * 1024 * 1024 + 1);
        if (bytes.length > 12 * 1024 * 1024) throw new IOException("生成图片文件过大");
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
          var readers = ImageIO.getImageReaders(input);
          if (!readers.hasNext()) throw new IOException("无效图片格式");
          var reader = readers.next();
          try {
            reader.setInput(input);
            String format = reader.getFormatName().toLowerCase(Locale.ROOT);
            if (!java.util.List.of("png", "jpg", "jpeg").contains(format)) throw new IOException(
              "图片格式不支持"
            );
            int w = reader.getWidth(0),
              h = reader.getHeight(0);
            if (w < 1 || h < 1 || w > 8192 || h > 8192) throw new IOException("无效图片尺寸");
            var param = reader.getDefaultReadParam();
            int sample = Math.max(1, Math.max(w, h) / 1024);
            param.setSourceSubsampling(sample, sample, 0, 0);
            BufferedImage source = reader.read(0, param);
            double scale = Math.min(1, 512.0 / Math.max(source.getWidth(), source.getHeight()));
            BufferedImage result = new BufferedImage(
              Math.max(1, (int) (source.getWidth() * scale)),
              Math.max(1, (int) (source.getHeight() * scale)),
              BufferedImage.TYPE_INT_RGB
            );
            Graphics2D g = result.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, result.getWidth(), result.getHeight());
            g.setRenderingHint(
              RenderingHints.KEY_INTERPOLATION,
              RenderingHints.VALUE_INTERPOLATION_BICUBIC
            );
            g.drawImage(source, 0, 0, result.getWidth(), result.getHeight(), null);
            g.dispose();
            var output = new ByteArrayOutputStream();
            ImageIO.write(result, "jpeg", output);
            return new GeneratedImage(
              validate(
                "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(output.toByteArray())
              ),
              bytes,
              format.equals("png") ? "image/png" : "image/jpeg"
            );
          } finally {
            reader.dispose();
          }
        }
      }
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      throw new ApiException(
        HttpStatus.BAD_GATEWAY,
        "IMAGE_DOWNLOAD_FAILED",
        "图片已生成但暂时无法保存，请稍后重试"
      );
    }
  }
}
