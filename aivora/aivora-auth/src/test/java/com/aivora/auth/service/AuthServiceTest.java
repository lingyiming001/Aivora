package com.aivora.auth.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivora.common.ApiException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

  private AuthService service(String role) {
    JdbcTemplate db = mock(JdbcTemplate.class);
    PasswordEncoder encoder = mock(PasswordEncoder.class);
    when(
      db.query(
        startsWith("select id,password_hash"),
        any(RowMapper.class),
        eq("test@example.com"),
        eq("test@example.com"),
        eq("test@example.com")
      )
    ).thenReturn(java.util.Collections.singletonList(new Object[] { 1L, "hash", role, "ACTIVE" }));
    when(encoder.matches("password", "hash")).thenReturn(true);
    return new AuthService(db, encoder, "test-secret-for-auth-isolation");
  }

  @Test
  void userCannotUseAdminPortal() {
    assertEquals(
      "WRONG_LOGIN_PORTAL",
      assertThrows(ApiException.class, () ->
        service("USER").login("test@example.com", "password", true)
      ).code()
    );
  }

  @Test
  void adminCannotUseUserPortal() {
    assertEquals(
      "WRONG_LOGIN_PORTAL",
      assertThrows(ApiException.class, () ->
        service("ADMIN").login("test@example.com", "password")
      ).code()
    );
  }

  @Test
  void adminCanUseAdminPortal() {
    assertNotNull(service("ADMIN").login("test@example.com", "password", true).accessToken());
  }

  @Test
  void userCanUseUserPortal() {
    assertNotNull(service("USER").login("test@example.com", "password").accessToken());
  }

  @Test
  void invalidPasswordIsRejected() {
    assertEquals(
      "BAD_CREDENTIALS",
      assertThrows(ApiException.class, () ->
        service("ADMIN").login("test@example.com", "wrong", true)
      ).code()
    );
  }

  @Test
  void newLoginExpiresInOneWeek() {
    long before = System.currentTimeMillis();
    var tokens = service("USER").login("test@example.com", "password");
    assertTrue(tokens.expiresAt() >= before + 7L * 86400000);
    assertTrue(tokens.expiresAt() <= System.currentTimeMillis() + 7L * 86400000);
    assertTrue(
      com.auth0.jwt.JWT.decode(tokens.accessToken()).getExpiresAt().getTime() < tokens.expiresAt()
    );
  }

  @Test
  void refreshPreservesOriginalDeadlineAndCapsAccessToken() {
    JdbcTemplate db = mock(JdbcTemplate.class);
    var expiry = java.time.Instant.now().plusSeconds(60);
    var uuid = java.util.UUID.randomUUID();
    when(
      db.query(startsWith("select r.id,r.user_id"), any(RowMapper.class), anyString())
    ).thenReturn(
      java.util.Collections.singletonList(new Object[] { uuid, 1L, "USER", "ACTIVE", expiry })
    );
    var tokens = new AuthService(
      db,
      mock(PasswordEncoder.class),
      "test-secret-for-auth-isolation"
    ).refresh("refresh");
    assertEquals(expiry.toEpochMilli(), tokens.expiresAt());
    assertTrue(
      com.auth0.jwt.JWT.decode(tokens.accessToken()).getExpiresAt().getTime() <=
        expiry.toEpochMilli()
    );
    verify(db).update(
      eq("insert into refresh_token(id,user_id,token_hash,expires_at) values (?,?,?,?)"),
      any(java.util.UUID.class),
      eq(1L),
      anyString(),
      eq(java.sql.Timestamp.from(expiry))
    );
  }

  @Test
  void oldThirtyDayTokenRefreshIsCappedToOneWeek() {
    JdbcTemplate db = mock(JdbcTemplate.class);
    when(
      db.query(startsWith("select r.id,r.user_id"), any(RowMapper.class), anyString())
    ).thenReturn(
      java.util.Collections.singletonList(new Object[] {
        java.util.UUID.randomUUID(),
        1L,
        "USER",
        "ACTIVE",
        java.time.Instant.now().plusSeconds(30L * 86400),
      })
    );
    var tokens = new AuthService(
      db,
      mock(PasswordEncoder.class),
      "test-secret-for-auth-isolation"
    ).refresh("legacy");
    assertTrue(tokens.expiresAt() <= System.currentTimeMillis() + 7L * 86400000);
  }
}
