package com.aivora.ai.infrastructure;

import com.aivora.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class SecretCipher {

  private final byte[] key;

  public SecretCipher(@Value("${aivora.provider-key:}") String encodedKey) {
    if (encodedKey.isBlank()) {
      key = null;
      return;
    }
    byte[] decoded = Base64.getDecoder().decode(encodedKey);
    if (decoded.length != 32) throw new IllegalArgumentException(
      "PROVIDER_ENCRYPTION_KEY must be 32 bytes in Base64"
    );
    key = decoded;
  }

  public String encrypt(String plain) {
    if (plain == null || plain.isBlank()) return null;
    if (key == null) throw new ApiException(
      HttpStatus.SERVICE_UNAVAILABLE,
      "KEY_NOT_CONFIGURED",
      "未配置供应商密钥加密主密钥"
    );
    try {
      byte[] nonce = new byte[12];
      new SecureRandom().nextBytes(nonce);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
        Cipher.ENCRYPT_MODE,
        new SecretKeySpec(key, "AES"),
        new GCMParameterSpec(128, nonce)
      );
      byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
      byte[] output = new byte[nonce.length + encrypted.length];
      System.arraycopy(nonce, 0, output, 0, nonce.length);
      System.arraycopy(encrypted, 0, output, nonce.length, encrypted.length);
      return Base64.getEncoder().encodeToString(output);
    } catch (Exception e) {
      throw new IllegalStateException("密钥加密失败", e);
    }
  }

  public String decrypt(String ciphertext) {
    if (ciphertext == null || ciphertext.isBlank()) return null;
    if (key == null) throw new IllegalStateException("未配置供应商密钥加密主密钥");
    try {
      byte[] input = Base64.getDecoder().decode(ciphertext);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
        Cipher.DECRYPT_MODE,
        new SecretKeySpec(key, "AES"),
        new GCMParameterSpec(128, Arrays.copyOfRange(input, 0, 12))
      );
      return new String(
        cipher.doFinal(Arrays.copyOfRange(input, 12, input.length)),
        StandardCharsets.UTF_8
      );
    } catch (Exception e) {
      throw new IllegalStateException("供应商密钥解密失败", e);
    }
  }
}
