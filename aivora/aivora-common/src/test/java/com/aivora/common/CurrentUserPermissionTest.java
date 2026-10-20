package com.aivora.common;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.*;

class CurrentUserPermissionTest {

  JdbcTemplate db;
  CurrentUser current;

  @BeforeEach
  void setup() {
    db = mock(JdbcTemplate.class);
    current = new CurrentUser(db);
    SecurityContextHolder.getContext().setAuthentication(
      new UsernamePasswordAuthenticationToken(7L, null, List.of())
    );
    when(db.queryForObject(contains("select role"), eq(String.class), eq(7L))).thenReturn("ADMIN");
    when(db.queryForObject(contains("select admin_super"), eq(Boolean.class), eq(7L))).thenReturn(
      false
    );
    when(db.queryForList(anyString(), eq(String.class), eq(7L))).thenReturn(List.of("review"));
  }

  void request(String path) {
    var r = new MockHttpServletRequest();
    r.setRequestURI("/api/admin/" + path);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(r));
  }

  @AfterEach
  void cleanup() {
    RequestContextHolder.resetRequestAttributes();
    SecurityContextHolder.clearContext();
  }

  @Test
  void cardPermissionAllowsReviewAndPrices() {
    request("characters/1/approve");
    assertDoesNotThrow(() -> current.admin());
    request("characters/1/price");
    assertDoesNotThrow(() -> current.admin());
  }

  @Test
  void otherMenusAreDenied() {
    request("users");
    assertEquals("MENU_FORBIDDEN", assertThrows(ApiException.class, () -> current.admin()).code());
  }

  @Test
  void accessIsSuperAdminOnlyEvenIfGranted() {
    when(db.queryForList(anyString(), eq(String.class), eq(7L))).thenReturn(List.of("access"));
    request("access/2");
    assertThrows(ApiException.class, () -> current.admin());
  }

  @Test
  void superAdminHasEveryMenu() {
    when(db.queryForObject(contains("select admin_super"), eq(Boolean.class), eq(7L))).thenReturn(
      true
    );
    request("access/2");
    assertDoesNotThrow(() -> current.admin());
    assertEquals(12, current.menus().size());
  }

  @Test
  void ordinaryUserCannotEnterAdmin() {
    when(db.queryForObject(contains("select role"), eq(String.class), eq(7L))).thenReturn("USER");
    request("characters/review");
    assertEquals("FORBIDDEN", assertThrows(ApiException.class, () -> current.admin()).code());
  }
}
