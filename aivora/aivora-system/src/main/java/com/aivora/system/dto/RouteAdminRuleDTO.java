package com.aivora.system.dto;

import jakarta.validation.constraints.*;
import java.util.*;

public record RouteAdminRuleDTO(
  @NotBlank @Size(max = 80) String name,
  @Pattern(regexp = "GUEST|STANDARD|VIP|SVIP") @NotNull String userTier,
  @Min(1) int minStars,
  @Min(1) Integer maxStars,
  @Positive long modelId,
  @Min(0) int priority,
  boolean enabled,
  @Size(max = 3) List<@Positive Long> helperModelIds,
  @Min(0) @Max(10000) int minCharacters
) {}
