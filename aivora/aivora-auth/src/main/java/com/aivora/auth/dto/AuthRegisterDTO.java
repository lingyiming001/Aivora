package com.aivora.auth.dto;

import jakarta.validation.constraints.*;

public record AuthRegisterDTO(
  @NotBlank @Email String email,
  @NotBlank @Size(min = 8, max = 72) String password,
  @NotBlank @Size(max = 80) String nickname,
  @Pattern(regexp = "[a-zA-Z][a-zA-Z0-9_]{2,31}") String username
) {}
