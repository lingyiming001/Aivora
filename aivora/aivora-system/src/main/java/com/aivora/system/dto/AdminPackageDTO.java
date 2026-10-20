package com.aivora.system.dto;

import jakarta.validation.constraints.*;
import java.util.*;

public record AdminPackageDTO(
  @NotBlank String name,
  @Positive long amountCents,
  @Positive long stars
) {}
