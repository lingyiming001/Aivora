package com.aivora.auth.dto;

import jakarta.validation.constraints.*;
import java.util.Map;

public record PhoneAuthLoginDTO(
  @NotBlank @Pattern(regexp = "1[3-9][0-9]{9}") String phone,
  @NotBlank @Pattern(regexp = "[0-9]{6}") String code
) {}
