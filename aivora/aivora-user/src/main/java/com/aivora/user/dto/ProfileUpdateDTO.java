package com.aivora.user.dto;

import jakarta.validation.constraints.*;
import java.util.*;

public record ProfileUpdateDTO(
  @NotBlank @Size(max = 80) String nickname,
  @NotNull @Size(max = 300) String bio,
  @NotBlank @Size(max = 300000) String avatarUrl
) {}
