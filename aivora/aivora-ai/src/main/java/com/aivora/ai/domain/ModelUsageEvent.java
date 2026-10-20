package com.aivora.ai.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

public record ModelUsageEvent(UUID id, String model, String kind, Long tokens, boolean succeeded) {
  public static Long tokens(JsonNode usage) {
    if (usage == null || !usage.isObject()) return null;
    var total = usage.get("total_tokens");
    if (
      total != null && total.isIntegralNumber() && total.canConvertToLong() && total.asLong() >= 0
    ) return total.asLong();
    var input = usage.has("prompt_tokens") ? usage.get("prompt_tokens") : usage.get("input_tokens");
    var output = usage.has("completion_tokens")
      ? usage.get("completion_tokens")
      : usage.get("output_tokens");
    if (
      input == null ||
      output == null ||
      !input.isIntegralNumber() ||
      !output.isIntegralNumber() ||
      !input.canConvertToLong() ||
      !output.canConvertToLong() ||
      input.asLong() < 0 ||
      output.asLong() < 0
    ) return null;
    try {
      return Math.addExact(input.asLong(), output.asLong());
    } catch (ArithmeticException e) {
      return null;
    }
  }
}
