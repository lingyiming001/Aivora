package com.aivora.system.dto;

import jakarta.validation.constraints.*;
import java.util.*;

public record RoleRoleDTO(
  @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{1,63}") String code,
  @NotBlank @Size(max = 80) String name,
  @NotNull @Size(max = 300) String description,
  boolean enabled,
  @NotNull List<String> menus
) {}
