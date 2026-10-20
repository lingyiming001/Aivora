package com.aivora.ai.domain;

import java.util.List;
import java.util.function.Consumer;

public interface AiGateway {
  record Message(String role, String content) {}

  void stream(
    String baseUrl,
    String apiKey,
    String model,
    List<Message> messages,
    Consumer<String> onDelta
  );
}
