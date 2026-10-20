package com.aivora.chat.dto;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record ChatMediaEditDTO(
  @NotNull UUID attachmentId,
  @NotBlank @Size(max = 800) String prompt
) {}
