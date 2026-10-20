package com.aivora.auth.dto;

import jakarta.validation.constraints.*;

public record AdminPhoneCodeDTO(
  @NotBlank @Pattern(regexp = "OLD|NEW") String purpose,
  String phone,
  @NotBlank @Size(max = 128) String currentPassword
) {}
