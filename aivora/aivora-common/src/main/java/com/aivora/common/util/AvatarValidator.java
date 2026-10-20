package com.aivora.common.util;

import com.aivora.common.ApiException;
import java.io.ByteArrayInputStream;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;

public final class AvatarValidator {

  private AvatarValidator() {}

  public static String validateAvatar(String avatar) {
    if (avatar != null && avatar.matches("/images/profile-[1-6]\\.svg")) return avatar;
    try {
      if (
        avatar == null ||
        avatar.length() > 300000 ||
        !avatar.matches("(?s)data:image/(png|jpeg);base64,[A-Za-z0-9+/=]+")
      ) throw new IllegalArgumentException();
      byte[] bytes = Base64.getDecoder().decode(avatar.substring(avatar.indexOf(',') + 1));
      if (bytes.length > 220000) throw new IllegalArgumentException();
      try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
        var readers = ImageIO.getImageReaders(input);
        if (!readers.hasNext()) throw new IllegalArgumentException();
        var reader = readers.next();
        try {
          reader.setInput(input);
          String format = reader.getFormatName().toLowerCase(Locale.ROOT);
          if (
            !List.of("png", "jpeg", "jpg").contains(format) ||
            reader.getWidth(0) > 1024 ||
            reader.getHeight(0) > 1024 ||
            reader.getWidth(0) < 1 ||
            reader.getHeight(0) < 1
          ) throw new IllegalArgumentException();
          if (reader.read(0) == null) throw new IllegalArgumentException();
        } finally {
          reader.dispose();
        }
      }
      return avatar;
    } catch (Exception e) {
      throw new ApiException(
        HttpStatus.BAD_REQUEST,
        "INVALID_AVATAR",
        "请选择预设头像或上传有效 PNG/JPEG 图片"
      );
    }
  }
}
