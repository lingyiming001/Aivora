package com.aivora.character.controller;

import static org.junit.jupiter.api.Assertions.*;

import com.aivora.common.ApiException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class CharacterAvatarTest {

  @Test
  void uploadedImageAccepted() throws Exception {
    var out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(512, 512, BufferedImage.TYPE_INT_RGB), "jpeg", out);
    String avatar =
      "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
    assertEquals(avatar, CharacterController.avatar(avatar));
  }

  @Test
  void rejectsInvalidDataAndKeepsExistingUrls() {
    assertThrows(ApiException.class, () ->
      CharacterController.avatar("data:image/jpeg;base64,aGVsbG8=")
    );
    assertThrows(ApiException.class, () ->
      CharacterController.avatar("data:image/svg+xml;base64,PHN2Zz4=")
    );
    assertEquals("/images/character-night.png", CharacterController.avatar(null));
    assertEquals(
      "https://example.com/a.png",
      CharacterController.avatar("https://example.com/a.png")
    );
  }
}
