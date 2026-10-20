package com.aivora.chat.controller;

import com.aivora.chat.dto.*;
import com.aivora.chat.service.ChatMediaService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chats/{id}/media")
public class ChatMediaController {

  private final ChatMediaService service;

  public ChatMediaController(ChatMediaService service) {
    this.service = service;
  }

  @GetMapping("/capabilities")
  public Object caps(@PathVariable long id) {
    return service.caps(id);
  }

  @PostMapping("/transcribe")
  public Object transcribe(@PathVariable long id, @Valid @RequestBody ChatMediaAudioDTO req) {
    return service.transcribe(id, req);
  }

  @PostMapping("/describe")
  public Object describe(@PathVariable long id, @Valid @RequestBody ChatMediaImageDTO req) {
    return service.describe(id, req);
  }

  @PostMapping("/speech")
  public Object speech(@PathVariable long id, @Valid @RequestBody ChatMediaSpeechDTO req) {
    return service.speech(id, req);
  }

  @GetMapping("/speech/{messageId}/audio")
  public org.springframework.http.ResponseEntity<byte[]> speechAudio(
    @PathVariable long id,
    @PathVariable long messageId
  ) {
    return org.springframework.http.ResponseEntity.ok()
      .contentType(org.springframework.http.MediaType.parseMediaType("audio/wav"))
      .header("Cache-Control", "private, no-store")
      .body(service.speechAudio(id, messageId));
  }

  @PostMapping("/draw")
  public Object draw(@PathVariable long id, @Valid @RequestBody ChatMediaPictureDTO req) {
    return service.draw(id, req);
  }

  @PostMapping("/edit")
  public Object edit(@PathVariable long id, @Valid @RequestBody ChatMediaEditDTO req) {
    return service.editImage(id, req);
  }

  @GetMapping("/images/{attachmentId}/download")
  public org.springframework.http.ResponseEntity<byte[]> download(
    @PathVariable long id,
    @PathVariable java.util.UUID attachmentId
  ) {
    var image = service.downloadImage(id, attachmentId);
    String mime = (String) image.get("mime");
    return org.springframework.http.ResponseEntity.ok()
      .contentType(org.springframework.http.MediaType.parseMediaType(mime))
      .header(
        "Content-Disposition",
        "attachment; filename=\"Aivora-image." + (mime.equals("image/png") ? "png" : "jpg") + "\""
      )
      .body((byte[]) image.get("bytes"));
  }

  public static String validateAudio(String value) {
    return ChatMediaService.validateAudio(value);
  }

  public static String safeMediaUrl(String value) {
    return ChatMediaService.safeMediaUrl(value);
  }
}
