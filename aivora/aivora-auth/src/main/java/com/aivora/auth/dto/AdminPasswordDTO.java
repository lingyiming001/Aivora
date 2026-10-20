package com.aivora.auth.dto;

import jakarta.validation.constraints.*;

public record AdminPasswordDTO(
  @NotBlank @Size(max = 128) String currentPassword,
  @NotBlank @Size(min = 8, max = 64) String newPassword
) {}
