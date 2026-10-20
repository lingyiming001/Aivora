package com.aivora.auth.dto;

import jakarta.validation.constraints.*;

public record AuthLoginDTO(String email, String account, @NotBlank String password) {
  public String identifier() {
    String value = account != null && !account.isBlank() ? account : email;
    if (value == null || value.isBlank()) throw new com.aivora.common.ApiException(
      org.springframework.http.HttpStatus.BAD_REQUEST,
      "ACCOUNT_REQUIRED",
      "请输入账号"
    );
    return value;
  }
}
