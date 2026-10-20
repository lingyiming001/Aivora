package com.aivora.auth.dto;

import jakarta.validation.constraints.*;

public record AuthRefreshDTO(@NotBlank String refreshToken) {}
