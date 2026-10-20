package com.aivora.auth.dto;

import jakarta.validation.constraints.*;

public record AdminProfileDTO(
  @NotBlank @Size(max = 80) String nickname,
  @NotNull @Size(max = 300) String bio,
  @NotBlank @Size(max = 300000) String avatarUrl
) {}
