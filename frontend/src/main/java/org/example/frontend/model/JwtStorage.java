package org.example.frontend.model;

import lombok.Getter;

public class JwtStorage {
  @Getter
  private static String token;
  public static void setToken(String token) {
    JwtStorage.token = token;
  }

}
