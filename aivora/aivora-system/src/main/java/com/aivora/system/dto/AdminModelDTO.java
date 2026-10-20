package com.aivora.system.dto;

import jakarta.validation.constraints.*;
import java.util.*;

public record AdminModelDTO(
  @Positive long providerId,
  @NotBlank String modelName,
  @Min(0) long starPerRequest
) {}
