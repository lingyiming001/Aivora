package com.aivora.chat.service;

import com.aivora.common.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import java.util.zip.*;
import javax.xml.stream.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ChatFileService {

  private final JdbcTemplate db;
  private final CurrentUser current;

  public ChatFileService(JdbcTemplate db, CurrentUser current) {
    this.db = db;
    this.current = current;
  }

  private long owner(long chat) {
    long user = current.id();
    if (current.guest()) throw error("LOGIN_REQUIRED", "登录后可添加文件");
    if (
      db
        .queryForList(
          "select s.id from chat_session s join character_card c on c.id=s.character_id where s.id=? and s.user_id=? and s.deleted_at is null and c.deleted_at is null and c.status<>'OFFLINE' and ((c.status='PUBLISHED' and c.visibility='PUBLIC') or c.owner_id=s.user_id)",
          chat,
          user
        )
        .isEmpty()
    ) throw new ApiException(HttpStatus.NOT_FOUND, "CHAT_UNAVAILABLE", "对话不可用");
    return user;
  }

  private ApiException error(String code, String message) {
    return new ApiException(HttpStatus.BAD_REQUEST, code, message);
  }

  public Object upload(long chat, String name, String data) {
    long user = owner(chat);
    name = name
      .substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf((char) 92)) + 1)
      .chars()
      .filter(ch -> !Character.isISOControl(ch))
      .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
      .toString()
      .trim();
    int dot = name.lastIndexOf('.');
    String ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    if (
      !Set.of("pdf", "docx", "txt", "md", "csv", "json", "log").contains(ext) || name.isBlank()
    ) throw error("UNSUPPORTED_FILE", "支持PDF、Word(.docx)、TXT、Markdown、CSV、JSON和日志文件");
    byte[] bytes;
    try {
      bytes = Base64.getDecoder().decode(data);
    } catch (Exception e) {
      throw error("INVALID_FILE", "文件编码无效，请重新添加");
    }
    if (bytes.length < 1 || bytes.length > 5 * 1024 * 1024) throw error(
      "FILE_TOO_LARGE",
      "请选择5MB以内的文件"
    );
    String text;
    boolean truncated = false;
    try {
      if (ext.equals("pdf")) {
        if (
          bytes.length < 5 || !new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")
        ) throw error("INVALID_FILE", "PDF文件格式无效");
        try (var document = Loader.loadPDF(bytes)) {
          if (document.isEncrypted()) throw error(
            "ENCRYPTED_FILE",
            "暂不支持加密PDF，请先解除密码保护"
          );
          var stripper = new PDFTextStripper();
          stripper.setEndPage(Math.min(50, document.getNumberOfPages()));
          StringBuilder extracted = new StringBuilder();
          Writer limited = new Writer() {
            public void write(char[] c, int off, int len) {
              int count = Math.min(len, 12001 - extracted.length());
              if (count > 0) extracted.append(c, off, count);
            }

            public void flush() {}

            public void close() {}
          };
          stripper.writeText(document, limited);
          text = extracted.toString();
          truncated = document.getNumberOfPages() > 50;
        }
      } else if (ext.equals("docx")) {
        text = word(bytes);
      } else {
        text = StandardCharsets.UTF_8.newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(bytes))
          .toString();
        if (text.indexOf(0) >= 0) throw error("INVALID_FILE", "请选择UTF-8编码的文本文件");
      }
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      throw error("UNREADABLE_FILE", "文件无法解析，请使用未加密PDF、有效Word或UTF-8文本文件");
    }
    text = text.replace(String.valueOf((char) 0), "").strip();
    if (text.isBlank()) throw error(
      "EMPTY_FILE",
      "没有可读取的文字；扫描版PDF请先转为可复制文字的文档或上传图片"
    );
    truncated = truncated || text.length() > 12000;
    text = text.substring(0, Math.min(text.length(), 12000));
    String mime = switch (ext) {
      case "pdf" -> "application/pdf";
      case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
      default -> "text/plain";
    };
    UUID id = UUID.randomUUID();
    db.update(
      "insert into chat_file_attachment(id,user_id,session_id,name,media_type,byte_size,file_data,extracted_text,truncated) values (?,?,?,?,?,?,?,?,?)",
      id,
      user,
      chat,
      name,
      mime,
      bytes.length,
      bytes,
      text,
      truncated
    );
    return Map.of(
      "id",
      id,
      "name",
      name,
      "media_type",
      mime,
      "byte_size",
      bytes.length,
      "truncated",
      truncated
    );
  }

  private String word(byte[] bytes) throws Exception {
    byte[] xml = null;
    int entries = 0,
      total = 0;
    try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        if (++entries > 512) throw error("INVALID_FILE", "Word文件结构过于复杂");
        byte[] part = zip.readNBytes(2 * 1024 * 1024 + 1);
        total += part.length;
        if (part.length > 2 * 1024 * 1024 || total > 8 * 1024 * 1024) throw error(
          "INVALID_FILE",
          "Word文件解压内容过大"
        );
        if (entry.getName().equals("word/document.xml")) xml = part;
      }
    }
    if (xml == null) throw error("INVALID_FILE", "不是有效的Word文档");
    var factory = XMLInputFactory.newFactory();
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
    var reader = factory.createXMLStreamReader(new ByteArrayInputStream(xml));
    var text = new StringBuilder();
    boolean run = false;
    try {
      while (reader.hasNext() && text.length() < 12001) {
        int event = reader.next();
        if (event == XMLStreamConstants.DTD) throw error("INVALID_FILE", "文档包含不支持的声明");
        if (event == XMLStreamConstants.START_ELEMENT && reader.getLocalName().equals("t")) run =
          true;
        if (event == XMLStreamConstants.CHARACTERS && run) {
          String value = reader.getText();
          text.append(value, 0, Math.min(value.length(), 12001 - text.length()));
        }
        if (event == XMLStreamConstants.END_ELEMENT) {
          if (reader.getLocalName().equals("t")) run = false;
          if (reader.getLocalName().equals("p")) text.append('\n');
        }
      }
    } finally {
      reader.close();
    }
    return text.toString();
  }

  private long reader(long chat) {
    long user = current.id();
    if (
      current.guest() ||
      db
        .queryForList(
          "select id from chat_session where id=? and user_id=? and deleted_at is null",
          chat,
          user
        )
        .isEmpty()
    ) throw error("FILE_UNAVAILABLE", "文件不可用");
    return user;
  }

  public Object preview(long chat, UUID id) {
    long user = reader(chat);
    var rows = db.queryForList(
      "select id,name,media_type,byte_size,truncated,extracted_text as text from chat_file_attachment where id=? and user_id=? and session_id=?",
      id,
      user,
      chat
    );
    if (rows.isEmpty()) throw error("FILE_UNAVAILABLE", "文件不可用");
    return rows.getFirst();
  }

  public Map<String, Object> download(long chat, UUID id) {
    long user = reader(chat);
    var rows = db.queryForList(
      "select name,file_data from chat_file_attachment where id=? and user_id=? and session_id=?",
      id,
      user,
      chat
    );
    if (rows.isEmpty()) throw error("FILE_UNAVAILABLE", "文件不可用");
    return rows.getFirst();
  }

  public java.util.List<Map<String, Object>> metadata(long chat, long message) {
    return db.queryForList(
      "select id,name,media_type,byte_size,truncated from chat_file_attachment where session_id=? and message_id=? order by created_at",
      chat,
      message
    );
  }

  public void prepare(long chat, java.util.List<UUID> ids) {
    if (ids == null || ids.isEmpty()) return;
    long user = owner(chat);
    if (ids.size() > 4 || new HashSet<>(ids).size() != ids.size()) throw error(
      "INVALID_FILES",
      "每条消息最多添加4个不同文件"
    );
    for (UUID id : ids)
      if (
        db
          .queryForList(
            "select id from chat_file_attachment where id=? and user_id=? and session_id=? and message_id is null for update",
            id,
            user,
            chat
          )
          .isEmpty()
      ) throw error("FILE_UNAVAILABLE", "附件不可用，请重新添加文件");
  }

  public void attach(long chat, String key, java.util.List<UUID> ids) {
    if (ids != null) for (UUID id : ids)
      db.update(
        "update chat_file_attachment set message_id=(select id from chat_message where session_id=? and request_key=? and role='user') where id=? and message_id is null",
        chat,
        key,
        id
      );
  }

  public String context(long chat, long message) {
    var rows = db.queryForList(
      "select name,extracted_text,truncated from chat_file_attachment where session_id=? and message_id=? order by created_at",
      chat,
      message
    );
    var text = new StringBuilder();
    for (var row : rows) {
      if (text.length() >= 12000) break;
      String part =
        "\n文件：" +
        row.get("name") +
        (Boolean.TRUE.equals(row.get("truncated")) ? "（节选）" : "") +
        "\n" +
        row.get("extracted_text");
      text.append(part, 0, Math.min(part.length(), 12000 - text.length()));
    }
    return text.toString();
  }
}
