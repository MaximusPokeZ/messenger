package org.example.frontend.utils;

import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

@Slf4j
public class RoomTokenEncoder {

  private static final String JOIN_DELIMITER = "|";
  private static final String SPLIT_DELIMITER = "\\|";

  public static String encode(String GUID, String cipher, String cipherMode, String paddingMode, String IV, String keyBitLength) {
    String token = GUID + JOIN_DELIMITER + cipher + JOIN_DELIMITER + cipherMode + JOIN_DELIMITER + paddingMode + JOIN_DELIMITER + IV + JOIN_DELIMITER + keyBitLength;
    return Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
  }

  public static DecodedRoomToken decode(String encodedToken) {
    String decoded = new String(Base64.getDecoder().decode(encodedToken), StandardCharsets.UTF_8);
    String[] parts = decoded.split(SPLIT_DELIMITER);
    log.info("decoded token: {}", decoded);
    if (parts.length != 6) throw new IllegalArgumentException("Invalid token format: " + decoded);
    return new DecodedRoomToken(parts[0], parts[1], parts[2], parts[3], parts[4], parts[5]);
  }

  public record DecodedRoomToken(String guid, String cipher, String cipherMode, String paddingMode, String IV, String keyBitLength) {}
}
