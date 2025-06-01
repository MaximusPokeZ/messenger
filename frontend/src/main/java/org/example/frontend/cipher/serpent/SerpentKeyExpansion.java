package org.example.frontend.cipher.serpent;


import org.example.frontend.cipher.interfaces.KeyExpansion;

public class SerpentKeyExpansion implements KeyExpansion {

  private static final int PHI = 0x9E3779B9;
  private static final int[] S_BOX_ORDER = {3, 2, 1, 0, 7, 6, 5, 4};

  @Override
  public byte[][] generateRoundKeys(byte[] inKey) {
    byte[] key = new byte[32];
    System.arraycopy(inKey, 0, key, 0, inKey.length);
    if (inKey.length < 32) {
      key[inKey.length] = (byte) 0x80;
    }

    int[] w = new int[132];
    for (int i = 0; i < 8; i++) {
      w[i] = (key[4 * i] & 0xFF) | ((key[4 * i + 1] & 0xFF) << 8) | ((key[4 * i + 2] & 0xFF) << 16) | ((key[4 * i + 3] & 0xFF) << 24);
    }

    for (int i = 8; i < 132; i++) {
      w[i] = Integer.rotateLeft((w[i - 8] ^ w[i - 5] ^ w[i - 3] ^ w[i - 1] ^ PHI ^ i), 11);
    }

    for (int block = 0; block < 33; block++) {
      int sBoxIndex = S_BOX_ORDER[block % 8];
      for (int i = 0; i < 4; i++) {
        w[block * 4 + i] = applySBox(w[block * 4 + i], sBoxIndex);
      }
    }

    byte[][] roundKeys = new byte[33][16]; // 33 ключа по 128 бит
    for (int i = 0; i < 33; i++) {
      for (int j = 0; j < 4; j++) {
        int word = w[i * 4 + j];
        roundKeys[i][j * 4] = (byte) (word);
        roundKeys[i][j * 4 + 1] = (byte) (word >>> 8);
        roundKeys[i][j * 4 + 2] = (byte) (word >>> 16);
        roundKeys[i][j * 4 + 3] = (byte) (word >>> 24);
      }
    }

    return roundKeys;
  }

  private int applySBox(int word, int index) {
    int res = 0;
    for (int i = 0; i < 8; i++) {
      int part = (word >>> (i * 4)) & 0x0F;
      res |= SerpentConstants.getSBoxValue(index, part) << (i * 4);
    }

    return res;
  }
}
