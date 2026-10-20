package com.aivora.system.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aivora.common.CurrentUser;
import com.aivora.system.service.LiveWorkspaceService;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;

class LiveMcpControllerTest {

  final ObjectMapper json = new ObjectMapper();
  final LiveWorkspaceService live = mock(LiveWorkspaceService.class);
  final CurrentUser current = mock(CurrentUser.class);
  final LiveMcpController controller = new LiveMcpController(live, current);

  JsonNode rpc(String s) throws Exception {
    return json.valueToTree(controller.rpc(json.readTree(s)).getBody());
  }

  @Test
  void rejectsUnsupportedHttpProtocol() throws Exception {
    assertEquals(400, controller.http(json.readTree("{}"), "2099-01-01").getStatusCode().value());
    verifyNoInteractions(live);
  }

  @Test
  void initializeAndListTools() throws Exception {
    assertEquals(
      "2025-11-25",
      rpc("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"}")
        .path("result")
        .path("protocolVersion")
        .asText()
    );
    assertEquals(
      4,
      rpc("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}")
        .path("result")
        .path("tools")
        .size()
    );
  }

  @Test
  void unknownMethodAndMalformedRequest() throws Exception {
    assertEquals(
      -32601,
      rpc("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"missing\"}")
        .path("error")
        .path("code")
        .asInt()
    );
    assertEquals(-32600, rpc("[]").path("error").path("code").asInt());
  }

  @Test
  void invalidRoomNeverCallsService() throws Exception {
    assertEquals(
      -32602,
      rpc(
        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"live_events\",\"arguments\":{\"roomId\":-1}}}"
      )
        .path("error")
        .path("code")
        .asInt()
    );
    verifyNoInteractions(live);
  }

  @Test
  void notificationsHaveNoResponse() throws Exception {
    assertEquals(
      202,
      controller
        .rpc(json.readTree("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"))
        .getStatusCode()
        .value()
    );
  }
}
