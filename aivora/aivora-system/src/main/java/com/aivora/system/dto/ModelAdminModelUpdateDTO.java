package com.aivora.system.dto;

import jakarta.validation.constraints.*;

public record ModelAdminModelUpdateDTO(
  @Positive long providerId,
  @NotBlank @Size(max = 120) String modelName,
  @Min(0) long starPerRequest,
  @Pattern(regexp = "TEXT|CHAT|VOICE|ASR|TTS|IMAGE|MULTIMODAL") String modelType,
  boolean enabled,
  boolean isDefault
) {}
