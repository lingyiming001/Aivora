package com.aivora.ai.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class SecretCipherTest {

  @Test
  void encryptsAndAuthenticatesProviderKey() {
    SecretCipher cipher = new SecretCipher(Base64.getEncoder().encodeToString(new byte[32]));
    String encoded = cipher.encrypt("secret-123");
    assertNotEquals("secret-123", encoded);
    assertEquals("secret-123", cipher.decrypt(encoded));
    byte[] corrupted = Base64.getDecoder().decode(encoded);
    corrupted[corrupted.length - 1] ^= 1;
    assertThrows(IllegalStateException.class, () ->
      cipher.decrypt(Base64.getEncoder().encodeToString(corrupted))
    );
  }
}
