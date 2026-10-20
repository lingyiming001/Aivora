package com.aivora.user.controller;

import com.aivora.common.util.AvatarValidator;
import com.aivora.user.dto.ProfileUpdateDTO;
import com.aivora.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

  private final UserService users;

  public ProfileController(UserService users) {
    this.users = users;
  }

  @GetMapping
  public Object profile() {
    return users.profile();
  }

  @PutMapping
  public void save(@Valid @RequestBody ProfileUpdateDTO request) {
    users.update(request);
  }

  public static String validateAvatar(String avatar) {
    return AvatarValidator.validateAvatar(avatar);
  }
}
