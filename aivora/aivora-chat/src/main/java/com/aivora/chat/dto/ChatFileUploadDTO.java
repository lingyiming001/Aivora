package com.aivora.chat.dto;

import jakarta.validation.constraints.*;

public record ChatFileUploadDTO(
  @NotBlank @Size(max = 160) String name,
  @NotBlank @Size(max = 7000000) String data
) {}
