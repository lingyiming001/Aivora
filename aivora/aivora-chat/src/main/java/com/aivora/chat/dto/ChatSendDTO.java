package com.aivora.chat.dto;

import jakarta.validation.constraints.*;
import java.util.*;

public record ChatSendDTO(
  @NotBlank @Size(max = 10000) String content,
  @Min(1) Long expectedStars,
  UUID attachmentId,
  @Size(max = 4) List<@NotNull UUID> fileIds
) {}
