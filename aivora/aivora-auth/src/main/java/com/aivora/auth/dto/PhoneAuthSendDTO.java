package com.aivora.auth.dto;

import jakarta.validation.constraints.*;
import java.util.Map;

public record PhoneAuthSendDTO(@NotBlank @Pattern(regexp = "1[3-9][0-9]{9}") String phone) {}
