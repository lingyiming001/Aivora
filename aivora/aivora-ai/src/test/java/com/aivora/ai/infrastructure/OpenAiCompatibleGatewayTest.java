package com.aivora.ai.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import com.aivora.ai.domain.AiGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleGatewayTest {

  private void stream(
    String payload,
    int status,
    java.util.function.Consumer<OpenAiCompatibleGateway> test
  ) throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/chat/completions", exchange -> {
      exchange.getRequestBody().readAllBytes();
      byte[] data = payload.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(status, data.length);
      exchange.getResponseBody().write(data);
      exchange.close();
    });
    server.start();
    try {
      url = "http://127.0.0.1:" + server.getAddress().getPort();
      test.accept(new OpenAiCompatibleGateway(new ObjectMapper()));
    } finally {
      server.stop(0);
    }
  }

  private String url;

  @Test
  void acceptsDataWithoutSpaceAndUnicode() throws Exception {
    stream("data:{\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}\n\ndata: [DONE]\n", 200, g -> {
      StringBuilder text = new StringBuilder();
      g.stream(url, "test", "model", List.of(new AiGateway.Message("user", "hi")), text::append);
      assertEquals("你好", text.toString());
    });
  }

  @Test
  void emptyGenerationFailsInsteadOfChargingForEmptyAnswer() throws Exception {
    stream("data: [DONE]\n", 200, g ->
      assertThrows(IllegalStateException.class, () ->
        g.stream(url, "test", "model", List.of(), x -> {})
      )
    );
  }

  @Test
  void providerErrorFails() throws Exception {
    stream("{}", 401, g ->
      assertThrows(IllegalStateException.class, () ->
        g.stream(url, "test", "model", List.of(), x -> {})
      )
    );
  }
}
