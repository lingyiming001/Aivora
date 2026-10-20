package com.aivora.user.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aivora.common.ApiException;
import com.aivora.common.CurrentUser;
import com.aivora.user.dto.ProfileUpdateDTO;
import com.aivora.user.mapper.UserMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UserServiceImplTest {

  @Test
  void profilePreservesResponseFields() {
    var mapper = mock(UserMapper.class);
    var current = mock(CurrentUser.class);
    when(current.id()).thenReturn(7L);
    when(mapper.activeRole(7L)).thenReturn("USER");
    var profile = Map.<String, Object>of(
      "id",
      7L,
      "avatar_url",
      "/images/profile-1.svg",
      "balance",
      42L
    );
    when(mapper.profile(7L)).thenReturn(profile);
    assertSame(profile, new UserServiceImpl(mapper, current).profile());
  }

  @Test
  void guestsCannotReadUserProfile() {
    var mapper = mock(UserMapper.class);
    var current = mock(CurrentUser.class);
    when(current.id()).thenReturn(7L);
    when(mapper.activeRole(7L)).thenReturn("GUEST");
    assertEquals(
      "USER_REQUIRED",
      assertThrows(ApiException.class, () -> new UserServiceImpl(mapper, current).profile()).code()
    );
    verify(mapper, never()).profile(7L);
  }

  @Test
  void updatePreservesTrimmingAndAvatarValidation() {
    var mapper = mock(UserMapper.class);
    var current = mock(CurrentUser.class);
    when(current.id()).thenReturn(7L);
    when(mapper.activeRole(7L)).thenReturn("USER");
    var service = new UserServiceImpl(mapper, current);
    service.update(new ProfileUpdateDTO(" User ", " Bio ", "/images/profile-1.svg"));
    verify(mapper).update(7L, "User", "Bio", "/images/profile-1.svg");
    assertEquals(
      "INVALID_AVATAR",
      assertThrows(ApiException.class, () ->
        service.update(new ProfileUpdateDTO("User", "Bio", "invalid"))
      ).code()
    );
  }
}
