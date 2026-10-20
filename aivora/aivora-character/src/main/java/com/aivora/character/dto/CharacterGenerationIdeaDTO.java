package com.aivora.character.dto;

import jakarta.validation.constraints.*;

public record CharacterGenerationIdeaDTO(@NotBlank @Size(max = 500) String idea) {}
