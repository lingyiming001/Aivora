package com.aivora.user.service;

import com.aivora.user.dto.ProfileUpdateDTO;
import java.util.Map;

public interface UserService {
  Map<String, Object> profile();
  void update(ProfileUpdateDTO request);
}
