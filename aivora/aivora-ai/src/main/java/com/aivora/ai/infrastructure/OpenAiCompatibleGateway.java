package com.aivora.ai.infrastructure;

import com.aivora.ai.domain.AiGateway;
import com.aivora.ai.domain.ModelUsageEvent;
import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class OpenAiCompatibleGateway implements AiGateway {

  private final ObjectMapper json;
  private final ApplicationEventPublisher publisher;
  private final HttpClient http = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(10))
    .build();

  public OpenAiCompatibleGateway(ObjectMapper json) {
    this(json, null);
  }

  @Autowired
  public OpenAiCompatibleGateway(ObjectMapper json, ApplicationEventPublisher publisher) {
    this.json = json;
    this.publisher = publisher;
  }

  @Override
  public void stream(
    String baseUrl,
    String apiKey,
    String model,
    List<Message> messages,
    Consumer<String> onDelta
  ) {
    Long tokens = null;
    boolean succeeded = false;
    try {
      String body = json.writeValueAsString(
        Map.of(
          "model",
          model,
          "messages",
          messages,
          "stream",
          true,
          "stream_options",
          Map.of("include_usage", true)
        )
      );
      HttpRequest.Builder builder = HttpRequest.newBuilder(
        URI.create(baseUrl.replaceAll("/+$", "") + "/chat/completions")
      )
        .timeout(Duration.ofMinutes(2))
        .header("Content-Type", "application/json");
      if (apiKey != null && !apiKey.isBlank()) builder.header("Authorization", "Bearer " + apiKey);
      HttpResponse<InputStream> response = http.send(
        builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
        HttpResponse.BodyHandlers.ofInputStream()
      );
      if (response.statusCode() / 100 != 2) {
        response.body().close();
        throw new IllegalStateException("模型供应商返回 HTTP " + response.statusCode());
      }
      boolean contentReceived = false;
      try (
        var reader = new BufferedReader(
          new InputStreamReader(response.body(), StandardCharsets.UTF_8)
        )
      ) {
        String line;
        while ((line = reader.readLine()) != null) {
          if (!line.startsWith("data:")) continue;
          String data = line.substring(5).stripLeading();
          if ("[DONE]".equals(data)) break;
          JsonNode parsed = json.readTree(data);
          Long value = ModelUsageEvent.tokens(parsed.path("usage"));
          if (value != null) tokens = value;
          JsonNode chunk = parsed.path("choices").path(0).path("delta").path("content");
          if (chunk.isTextual() && !chunk.asText().isEmpty()) {
            contentReceived = true;
            onDelta.accept(chunk.asText());
          }
        }
      }
      if (!contentReceived) throw new IllegalStateException("供应商未返回文字内容");
      succeeded = true;
    } catch (Exception e) {
      throw new IllegalStateException("模型调用失败", e);
    } finally {
      if (publisher != null) try {
        publisher.publishEvent(
          new ModelUsageEvent(UUID.randomUUID(), model, "CHAT", tokens, succeeded)
        );
      } catch (Exception ignored) {}
    }
  }
}
