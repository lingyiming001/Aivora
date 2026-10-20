package com.aivora.character.controller;

import com.aivora.character.dto.*;
import com.aivora.character.service.CharacterGenerationService;
import com.aivora.common.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/characters")
public class CharacterGenerationController {

  private final CharacterGenerationService service;
  private final CurrentUser current;

  public CharacterGenerationController(CharacterGenerationService service, CurrentUser current) {
    this.service = service;
    this.current = current;
  }

  @PostMapping("/generate")
  public CharacterGenerationService.Draft generate(
    @Valid @RequestBody CharacterGenerationIdeaDTO request
  ) {
    return service.generate(current.id(), request.idea().trim());
  }
}
