package org.example.frontend.utils;

import java.util.Base64;
import java.util.UUID;

public class RoomTokenEncoder {

  public static String encode(String GUID, String cipher, String cipherMode, String paddingMode, String IV) {
    String token = GUID + "." + cipher + "." + cipherMode + "." + paddingMode + "." + IV;
    return Base64.getEncoder().encodeToString(token.getBytes());
  }

  public static DecodedRoomToken decode(String encodedToken) {
    String decoded = new String(Base64.getDecoder().decode(encodedToken));
    String[] parts = decoded.split("\\.");
    if (parts.length != 5) throw new IllegalArgumentException("Invalid token format");
    return new DecodedRoomToken(parts[0], parts[1], parts[2], parts[3], parts[4]);
  }

  public record DecodedRoomToken(String guid, String cipher, String cipherMode, String paddingMode, String IV) {}
}
