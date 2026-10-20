package com.aivora.ai.domain;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ModelUsageEventTest {

  private final ObjectMapper json = new ObjectMapper();

  @Test
  void parsesProviderReportedTotals() throws Exception {
    assertEquals(13L, ModelUsageEvent.tokens(json.readTree("{\"total_tokens\":13}")));
    assertEquals(
      12L,
      ModelUsageEvent.tokens(json.readTree("{\"prompt_tokens\":9,\"completion_tokens\":3}"))
    );
    assertEquals(
      7L,
      ModelUsageEvent.tokens(json.readTree("{\"input_tokens\":5,\"output_tokens\":2}"))
    );
  }

  @Test
  void distinguishesMissingFromReportedZero() throws Exception {
    assertNull(ModelUsageEvent.tokens(json.readTree("{\"characters\":120}")));
    assertNull(ModelUsageEvent.tokens(json.readTree("{\"total_tokens\":-1}")));
    assertNull(ModelUsageEvent.tokens(json.readTree("{\"total_tokens\":1.5}")));
    assertEquals(
      0L,
      ModelUsageEvent.tokens(json.readTree("{\"input_tokens\":0,\"output_tokens\":0}"))
    );
  }

  @Test
  void rejectsOverflow() throws Exception {
    assertNull(
      ModelUsageEvent.tokens(
        json.readTree("{\"prompt_tokens\":9223372036854775807,\"completion_tokens\":1}")
      )
    );
  }
}
