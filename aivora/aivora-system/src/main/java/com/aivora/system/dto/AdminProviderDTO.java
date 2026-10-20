package com.aivora.system.dto;

import jakarta.validation.constraints.*;

public record AdminProviderDTO(
  @NotBlank @Size(max = 80) String name,
  @NotBlank @Pattern(regexp = "OPENAI_COMPATIBLE") String kind,
  @NotBlank @Size(max = 500) String baseUrl,
  @Size(max = 4096) String apiKey,
  boolean enabled
) {}
