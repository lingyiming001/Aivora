package com.aivora.auth.dto;

import jakarta.validation.constraints.*;

public record AdminCreateAccountDTO(
  @NotBlank @Email @Size(max = 320) String email,
  @NotBlank @Size(max = 80) String nickname,
  @NotBlank @Size(min = 8, max = 64) String password,
  boolean enabled,
  @NotNull @Size(max = 50) java.util.List<@NotNull Long> roleIds
) {}
