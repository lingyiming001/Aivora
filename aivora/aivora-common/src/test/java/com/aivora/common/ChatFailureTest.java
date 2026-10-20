package com.aivora.common;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ChatFailureTest {

  @Test
  void findsNestedConnectionFailure() {
    assertEquals(
      "AI_UNREACHABLE",
      ChatFailure.describe(
        new IllegalStateException("wrapped", new java.net.ConnectException())
      ).code()
    );
  }

  @Test
  void reportsTimeout() {
    assertEquals(
      "AI_TIMEOUT",
      ChatFailure.describe(new java.util.concurrent.TimeoutException()).code()
    );
  }

  @Test
  void doesNotExposeArbitraryExceptionMessages() {
    assertFalse(
      ChatFailure.describe(new IllegalArgumentException("secret-key-value"))
        .message()
        .contains("secret-key-value")
    );
  }
}
