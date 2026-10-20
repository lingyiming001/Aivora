package com.aivora.chat.dto;

import jakarta.validation.constraints.*;
import java.util.*;

public record BatchChatDeleteDTO(
  @NotNull @Size(min = 1, max = 100) List<@NotNull @Positive Long> ids,
  @NotNull @AssertTrue Boolean confirmed
) {}
