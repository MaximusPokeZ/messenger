package org.example.frontend.cipher.serpent;

public class SerpentConfigurationFactory {
  public static SerpentConfiguration serpent_128() {
    return new SerpentConfiguration(128);
  }

  public static SerpentConfiguration serpent_192() {
    return new SerpentConfiguration(192);
  }

  public static SerpentConfiguration serpent_256() {
    return new SerpentConfiguration(256);
  }
}
