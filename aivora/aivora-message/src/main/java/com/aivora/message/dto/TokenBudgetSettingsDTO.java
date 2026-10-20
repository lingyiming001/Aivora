package com.aivora.message.dto;

import jakarta.validation.constraints.*;

public record TokenBudgetSettingsDTO(
  @NotBlank String month,
  @Min(1) @Max(1000000000000L) long limit,
  @Min(1) @Max(99) int threshold,
  @Min(0) @Max(1000000000000L) long baseline,
  boolean enabled
) {}
