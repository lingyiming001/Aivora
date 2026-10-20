package com.aivora.user.service.impl;

import com.aivora.common.ApiException;
import com.aivora.common.CurrentUser;
import com.aivora.common.util.AvatarValidator;
import com.aivora.user.dto.ProfileUpdateDTO;
import com.aivora.user.mapper.UserMapper;
import com.aivora.user.service.UserService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

  private final UserMapper mapper;
  private final CurrentUser current;

  public UserServiceImpl(UserMapper mapper, CurrentUser current) {
    this.mapper = mapper;
    this.current = current;
  }

  private long user() {
    long id = current.id();
    if (!"USER".equals(mapper.activeRole(id))) throw new ApiException(
      HttpStatus.FORBIDDEN,
      "USER_REQUIRED",
      "请使用用户账号登录"
    );
    return id;
  }

  @Override
  public Map<String, Object> profile() {
    return mapper.profile(user());
  }

  @Override
  public void update(ProfileUpdateDTO request) {
    long id = user();
    String avatar = AvatarValidator.validateAvatar(request.avatarUrl());
    mapper.update(id, request.nickname().trim(), request.bio().trim(), avatar);
  }
}
