package com.aivora.chat.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivora.common.ApiException;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class RoutingServiceTest {

  private JdbcTemplate db(String role, String tier, long price) {
    JdbcTemplate db = mock(JdbcTemplate.class);
    when(db.queryForList(startsWith("select c.*"), eq(5L), eq(7L))).thenReturn(
      List.of(Map.of("role", role, "membership_tier", tier, "stars_per_message", price))
    );
    return db;
  }

  @Test
  void guestCannotInheritMemberTier() {
    JdbcTemplate db = db("GUEST", "SVIP", 3);
    when(
      db.queryForList(startsWith("select m.id,m.model_name"), eq("GUEST"), eq(3L), eq(3L))
    ).thenReturn(List.of(Map.of("id", 11L)));
    var route = new RoutingService(db).resolve(7, 5);
    assertEquals("GUEST", route.tier());
    assertEquals(11, route.modelId());
    assertEquals(3, route.stars());
  }

  @Test
  void memberTierAndCharacterPriceSelectRoute() {
    JdbcTemplate db = db("USER", "VIP", 5);
    when(
      db.queryForList(startsWith("select m.id,m.model_name"), eq("VIP"), eq(5L), eq(5L))
    ).thenReturn(List.of(Map.of("id", 22L)));
    var route = new RoutingService(db).resolve(7, 5);
    assertEquals(22, route.modelId());
    assertEquals(5, route.stars());
  }

  @Test
  void noMatchingRuleUsesEnabledDefault() {
    JdbcTemplate db = db("USER", "STANDARD", 1);
    when(db.queryForList(contains("m.is_default"))).thenReturn(List.of(Map.of("id", 33L)));
    assertEquals(33, new RoutingService(db).resolve(7, 5).modelId());
  }

  @Test
  void noAvailableModelFailsBeforeCharging() {
    JdbcTemplate db = db("USER", "STANDARD", 1);
    assertEquals(
      "NO_AVAILABLE_ROUTE",
      assertThrows(ApiException.class, () -> new RoutingService(db).resolve(7, 5)).code()
    );
  }

  @Test
  void inaccessiblePrivateCharacterCannotRoute() {
    JdbcTemplate db = mock(JdbcTemplate.class);
    assertEquals(
      "CHARACTER_UNAVAILABLE",
      assertThrows(ApiException.class, () -> new RoutingService(db).resolve(7, 5)).code()
    );
  }

  @Test
  void availableConfiguredRealModelUsedWhenDefaultMissing() {
    var db = db("USER", "STANDARD", 1);
    when(db.queryForList(contains("order by m.id limit 1"))).thenReturn(List.of(Map.of("id", 77L)));
    assertEquals(77, new RoutingService(db).resolve(7, 5).modelId());
  }

  @Test
  void creationRequiresActiveUser() {
    var db = mock(JdbcTemplate.class);
    assertEquals(
      "USER_REQUIRED",
      assertThrows(ApiException.class, () -> new RoutingService(db).creationModel(7)).code()
    );
  }

  @Test
  void creationUsesMemberRouteWithoutCreatingCharacter() {
    var db = mock(JdbcTemplate.class);
    when(db.queryForList(startsWith("select membership_tier"), eq(7L))).thenReturn(
      List.of(Map.of("membership_tier", "VIP"))
    );
    when(
      db.queryForList(startsWith("select m.id,m.model_name"), eq("VIP"), eq(1L), eq(1L))
    ).thenReturn(List.of(Map.of("id", 22L)));
    assertEquals(22L, new RoutingService(db).creationModel(7).get("id"));
  }
}
