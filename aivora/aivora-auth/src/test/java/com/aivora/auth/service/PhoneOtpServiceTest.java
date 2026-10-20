package com.aivora.auth.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivora.common.ApiException;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;

class PhoneOtpServiceTest {

  private PhoneOtpService service(JdbcTemplate db, AuthService auth, boolean dev) {
    MockEnvironment env = new MockEnvironment();
    if (dev) env.setActiveProfiles("dev");
    return new PhoneOtpService(db, auth, env, "test-otp-hmac-secret");
  }

  @Test
  void productionCannotSendOrAcceptDevelopmentCodes() {
    JdbcTemplate db = mock(JdbcTemplate.class);
    PhoneOtpService s = service(db, mock(AuthService.class), false);
    assertEquals(
      "SMS_NOT_CONFIGURED",
      assertThrows(ApiException.class, () -> s.send("19900000001", false, "127.0.0.1")).code()
    );
    assertThrows(ApiException.class, () -> s.login("19900000001", "123456", false));
    verifyNoInteractions(db);
  }

  @Test
  void rejectsInvalidPhone() {
    assertThrows(ApiException.class, () -> PhoneOtpService.phone("123"));
    assertEquals("19900000001", PhoneOtpService.phone("19900000001"));
  }

  private Map<String, Object> challenge(UUID id, String hash, int attempts, boolean valid) {
    Map<String, Object> m = new HashMap<>();
    m.put("id", id);
    m.put("code_hash", hash);
    m.put("attempts", attempts);
    m.put("valid", valid);
    return m;
  }

  @Test
  void wrongCodeCountsAttemptWithoutIssuingToken() {
    JdbcTemplate db = mock(JdbcTemplate.class);
    AuthService auth = mock(AuthService.class);
    PhoneOtpService s = service(db, auth, true);
    UUID id = UUID.randomUUID();
    when(
      db.queryForList(startsWith("select id,code_hash"), eq("19900000001"), eq("USER"))
    ).thenReturn(List.of(challenge(id, s.digest(id, "123456"), 0, true)));
    assertThrows(ApiException.class, () -> s.login("19900000001", "000000", false));
    verify(db).update("update login_otp set attempts=attempts+1 where id=?", id);
    verifyNoInteractions(auth);
  }

  @Test
  void expiredAndExhaustedChallengesCannotLogin() {
    for (boolean expired : List.of(true, false)) {
      JdbcTemplate db = mock(JdbcTemplate.class);
      AuthService auth = mock(AuthService.class);
      PhoneOtpService s = service(db, auth, true);
      UUID id = UUID.randomUUID();
      when(
        db.queryForList(startsWith("select id,code_hash"), eq("19900000001"), eq("USER"))
      ).thenReturn(List.of(challenge(id, s.digest(id, "123456"), expired ? 0 : 5, !expired)));
      assertThrows(ApiException.class, () -> s.login("19900000001", "123456", false));
      verifyNoInteractions(auth);
    }
  }

  @Test
  void validAdminChallengeIsConsumedBeforeAdminTokenIssued() {
    JdbcTemplate db = mock(JdbcTemplate.class);
    AuthService auth = mock(AuthService.class);
    PhoneOtpService s = service(db, auth, true);
    UUID id = UUID.randomUUID();
    when(
      db.queryForList(startsWith("select id,code_hash"), eq("19900000000"), eq("ADMIN"))
    ).thenReturn(List.of(challenge(id, s.digest(id, "123456"), 0, true)));
    when(auth.phoneLogin("19900000000", true)).thenReturn(
      new AuthService.Tokens("access", "refresh")
    );
    assertEquals("access", s.login("19900000000", "123456", true).accessToken());
    var order = inOrder(db, auth);
    order.verify(db).update("update login_otp set consumed_at=now() where id=?", id);
    order.verify(auth).phoneLogin("19900000000", true);
  }
}
