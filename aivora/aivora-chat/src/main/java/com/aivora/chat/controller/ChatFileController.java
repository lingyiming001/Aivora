package com.aivora.chat.controller;

import com.aivora.chat.dto.ChatFileUploadDTO;
import com.aivora.chat.service.ChatFileService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chats/{id}/files")
public class ChatFileController {

  private final ChatFileService service;

  public ChatFileController(ChatFileService service) {
    this.service = service;
  }

  @PostMapping
  public Object upload(@PathVariable long id, @Valid @RequestBody ChatFileUploadDTO req) {
    return service.upload(id, req.name(), req.data());
  }

  @GetMapping("/{fileId}")
  public Object preview(@PathVariable long id, @PathVariable UUID fileId) {
    return service.preview(id, fileId);
  }

  @GetMapping("/{fileId}/download")
  public ResponseEntity<byte[]> download(@PathVariable long id, @PathVariable UUID fileId) {
    var file = service.download(id, fileId);
    return ResponseEntity.ok()
      .contentType(MediaType.APPLICATION_OCTET_STREAM)
      .header(
        HttpHeaders.CONTENT_DISPOSITION,
        ContentDisposition.attachment()
          .filename((String) file.get("name"), StandardCharsets.UTF_8)
          .build()
          .toString()
      )
      .body((byte[]) file.get("file_data"));
  }
}
