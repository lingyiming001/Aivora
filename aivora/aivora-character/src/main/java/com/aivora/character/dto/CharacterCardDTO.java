package com.aivora.character.dto;

import jakarta.validation.constraints.*;
import java.util.*;

public record CharacterCardDTO(
  @NotBlank @Size(max = 100) String name,
  @Size(max = 1000) String summary,
  @Size(max = 10000) String persona,
  @Size(max = 10000) String systemPrompt,
  @Size(max = 2000) String opening,
  @Size(max = 300000) String avatarUrl,
  String category,
  @Size(max = 200) String tags
) {}
