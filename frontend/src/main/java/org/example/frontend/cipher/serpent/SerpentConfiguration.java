package org.example.frontend.cipher.serpent;

public class SerpentConfiguration {
  public static final int BLOCK_SIZE = 16;
  public static final int NUM_ROUNDS = 32;
  private final int keySize;

  public SerpentConfiguration(int keySize) {
    if (keySize != 128 && keySize != 192 && keySize != 256) {
      throw new IllegalArgumentException("Serpent supports only 128, 192, or 256-bit keys");
    }
    this.keySize = keySize;
  }

  public int getKeySize() {
    return keySize;
  }

  public int getBlockSize() {
    return BLOCK_SIZE;
  }

  public int getNumRounds() {
    return NUM_ROUNDS;
  }

  public int getKeySizeInBytes() {
    return keySize / 8;
  }

  @Override
  public String toString() {
    return "SerpentConfiguration: " +
            "blockSize=" + BLOCK_SIZE +
            ", keySize=" + keySize +
            ", numRounds=" + NUM_ROUNDS;
  }
}
