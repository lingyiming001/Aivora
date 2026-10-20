package com.aivora.system.dto;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.validation.Validation;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelAdminModelUpdateDTOTest {

  @Test
  void acceptsEverySupportedModelType() {
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var validator = factory.getValidator();
      for (var type : List.of("TEXT", "CHAT", "VOICE", "ASR", "TTS", "IMAGE", "MULTIMODAL"))
        assertTrue(
          validator
            .validate(new ModelAdminModelUpdateDTO(1, "model", 0, type, false, false))
            .isEmpty(),
          type
        );
    }
  }

  @Test
  void rejectsUnknownModelType() {
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      assertTrue(
        factory
          .getValidator()
          .validate(new ModelAdminModelUpdateDTO(1, "model", 0, "UNKNOWN", false, false))
          .stream()
          .anyMatch(v -> v.getPropertyPath().toString().equals("modelType"))
      );
    }
  }

  @Test
  void retainsRequestValidation() {
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var violations = factory
        .getValidator()
        .validate(new ModelAdminModelUpdateDTO(0, "", -1, "TTS", false, false));
      assertEquals(3, violations.size());
    }
  }
}
