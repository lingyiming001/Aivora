package com.aivora.system.dto;

import jakarta.validation.constraints.*;
import java.util.*;

public record AdminAdjustmentDTO(
  @NotNull Long userId,
  @NotNull Long delta,
  @NotBlank String reason,
  @NotBlank String referenceKey
) {}
