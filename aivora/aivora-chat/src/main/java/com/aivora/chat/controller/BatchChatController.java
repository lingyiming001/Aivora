package com.aivora.chat.controller;

import com.aivora.chat.dto.*;
import com.aivora.chat.service.BatchChatService;
import com.aivora.common.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chats")
public class BatchChatController {

  private final BatchChatService service;
  private final CurrentUser current;

  public BatchChatController(BatchChatService service, CurrentUser current) {
    this.service = service;
    this.current = current;
  }

  @PostMapping("/batch-delete")
  public Object delete(@Valid @RequestBody BatchChatDeleteDTO r) {
    return Map.of("deleted", service.delete(current.id(), r.ids()));
  }
}
