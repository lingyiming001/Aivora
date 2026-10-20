package com.aivora.system.dto;

import jakarta.validation.constraints.*;

public record AdminMenuDTO(
  @NotBlank @Pattern(regexp = "[a-z][a-z0-9-]{1,39}") String key,
  @NotBlank @Size(max = 80) String name,
  @NotBlank @Size(max = 40) String groupName,
  @Size(max = 300) String description,
  @Min(0) int sortOrder
) {}
