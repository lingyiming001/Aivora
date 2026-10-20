package com.aivora.user.controller;

import static org.junit.jupiter.api.Assertions.*;

import com.aivora.common.ApiException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ProfileAvatarTest {

  @Test
  void presetsAreRestricted() {
    assertEquals(
      "/images/profile-1.svg",
      ProfileController.validateAvatar("/images/profile-1.svg")
    );
    for (String v : List.of(
      "/images/profile-9.svg",
      "https://tracking.example/avatar.png",
      "data:image/svg+xml;base64,PHN2Zz4=",
      "javascript:alert(1)"
    ))
      assertThrows(ApiException.class, () -> ProfileController.validateAvatar(v));
  }

  @Test
  void validLocalImageAccepted() throws Exception {
    var out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB), "png", out);
    String image = "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
    assertEquals(image, ProfileController.validateAvatar(image));
  }

  @Test
  void malformedImageRejected() {
    assertThrows(ApiException.class, () ->
      ProfileController.validateAvatar("data:image/png;base64,aGVsbG8=")
    );
  }

  @Test
  void excessiveDimensionsRejected() throws Exception {
    var out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(1025, 1, BufferedImage.TYPE_INT_RGB), "png", out);
    assertThrows(ApiException.class, () ->
      ProfileController.validateAvatar(
        "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray())
      )
    );
  }
}
