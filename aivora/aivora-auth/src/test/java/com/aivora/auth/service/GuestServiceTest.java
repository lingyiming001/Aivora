package com.aivora.auth.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivora.common.ApiException;
import jakarta.servlet.http.Cookie;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.*;
import org.springframework.security.crypto.password.PasswordEncoder;

class GuestServiceTest {

  @Test
  void noCookieDoesNotAuthenticate() {
    JdbcTemplate db = mock(JdbcTemplate.class);
    GuestService s = new GuestService(db, mock(PasswordEncoder.class), 10, "secret");
    assertNull(s.identify(new MockHttpServletRequest()));
    verifyNoInteractions(db);
  }

  @Test
  void existingGuestDoesNotReceiveAnotherGrant() {
    JdbcTemplate db = mock(JdbcTemplate.class);
    when(db.queryForList(startsWith("select g.user_id"), anyString())).thenReturn(
      List.of(Map.of("user_id", 9L))
    );
    when(
      db.queryForObject("select balance from wallet where user_id=?", Long.class, 9L)
    ).thenReturn(2L);
    MockHttpServletRequest req = new MockHttpServletRequest();
    req.setCookies(new Cookie(GuestService.COOKIE, "opaque-token"));
    Object state = new GuestService(db, mock(PasswordEncoder.class), 10, "secret").start(
      req,
      new MockHttpServletResponse()
    );
    assertEquals(2L, ((Map<?, ?>) state).get("balance"));
    verify(db, never()).update(startsWith("insert into wallet"), any(), any());
  }

  @Test
  void repeatedFreshSessionsHaveServerLimit() {
    JdbcTemplate db = mock(JdbcTemplate.class);
    when(
      db.queryForObject(
        startsWith("select count(*) from guest_session"),
        eq(Integer.class),
        anyString()
      )
    ).thenReturn(3);
    assertEquals(
      "GUEST_QUOTA_EXHAUSTED",
      assertThrows(ApiException.class, () ->
        new GuestService(db, mock(PasswordEncoder.class), 10, "secret").start(
          new MockHttpServletRequest(),
          new MockHttpServletResponse()
        )
      ).code()
    );
    verify(db, never()).queryForObject(
      startsWith("insert into app_user"),
      eq(Long.class),
      any(),
      any(),
      any()
    );
  }
}
