package com.aivora.common;

import com.aivora.common.ApiError;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiAdvice {

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ApiError> api(ApiException ex) {
    return ResponseEntity.status(ex.status()).body(new ApiError(ex.code(), ex.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex) {
    return ResponseEntity.badRequest().body(new ApiError("VALIDATION_ERROR", "请求参数无效"));
  }
}
