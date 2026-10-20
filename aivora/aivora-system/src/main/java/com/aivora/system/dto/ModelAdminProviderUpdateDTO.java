package com.aivora.system.dto;

import jakarta.validation.constraints.*;

public record ModelAdminProviderUpdateDTO(
  @NotBlank @Size(max = 500) String baseUrl,
  @Size(max = 4096) String apiKey,
  boolean enabled,
  boolean clearKey,
  @Size(max = 80) String name
) {}
