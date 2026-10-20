package com.aivora.auth.dto;

import jakarta.validation.constraints.*;

public record AdminPhoneBindDTO(
  @NotBlank @Pattern(regexp = "1[3-9][0-9]{9}") String phone,
  @NotBlank @Pattern(regexp = "[0-9]{6}") String newCode,
  @Pattern(regexp = "[0-9]{6}") String oldCode,
  @NotBlank @Size(max = 128) String currentPassword
) {}
