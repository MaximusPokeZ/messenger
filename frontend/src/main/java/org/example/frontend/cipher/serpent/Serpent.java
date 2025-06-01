package org.example.frontend.cipher.serpent;


import org.example.frontend.cipher.interfaces.EncryptorDecryptorSymmetric;
import org.example.frontend.cipher.interfaces.KeyExpansion;

public class Serpent implements EncryptorDecryptorSymmetric {

  private final SerpentConfiguration configuration;
  private final KeyExpansion keyExpansion;
  private byte[][] roundKeys = null;

  public Serpent(SerpentConfiguration configuration) {
    this.configuration = configuration;
    this.keyExpansion = new SerpentKeyExpansion();
  }

  @Override
  public void setKey(byte[] symmetricKey) {
    if (symmetricKey.length != configuration.getKeySizeInBytes()) {
      throw new IllegalArgumentException("Invalid key size: expected " + configuration.getKeySizeInBytes() + " but got " + symmetricKey.length);
    }
    this.roundKeys = keyExpansion.generateRoundKeys(symmetricKey);
  }

  @Override
  public byte[] encrypt(byte[] message) {
    if (roundKeys == null) {
      throw new IllegalStateException("Round keys are not set. Call setSymmetricKey first!");
    }

    int blockSize = getBlockSize();
    if (message.length != blockSize) {
      throw new IllegalArgumentException("Block length must be " + blockSize + " bytes but got " + message.length);
    }

    byte[] block = PermuteBits.permute(message, SerpentConstants.IP_TABLE, false, false);
    for (int round = 0; round < configuration.getNumRounds(); round++) {
      xor(block, roundKeys[round]);

      applySboxes(block, round, false);

      if (round != configuration.getNumRounds() - 1) {
        block = linearTransformation(block);
      }
    }
    xor(block, roundKeys[configuration.getNumRounds()]);

    block = PermuteBits.permute(block, SerpentConstants.FP_TABLE, false, false);

    return block;
  }

  @Override
  public byte[] decrypt(byte[] ciphertext) {
    if (roundKeys == null) {
      throw new IllegalStateException("Round keys are not set. Call setSymmetricKey first!");
    }

    int blockSize = getBlockSize();
    if (ciphertext.length != blockSize) {
      throw new IllegalArgumentException("Block length must be " + blockSize + " bytes but got " + ciphertext.length);
    }

    byte[] block = PermuteBits.permute(ciphertext, SerpentConstants.IP_TABLE, false, false);
    xor(block, roundKeys[configuration.getNumRounds()]);

    for (int round = configuration.getNumRounds() - 1; round >= 0; round--) {
      if (round != configuration.getNumRounds() - 1) {
        block = inverseLinearTransformation(block);
      }
      applySboxes(block, round, true);

      xor(block, roundKeys[round]);
    }

    block = PermuteBits.permute(block, SerpentConstants.FP_TABLE, false, false);

    return block;
  }

  @Override
  public int getBlockSize() {
    return configuration.getBlockSize();
  }

  private void applySboxes(byte[] block, int round, boolean invSbox) {
    int sBoxIndex = round % 8;

    for (int group = 0; group < 32; group++) {
      int byteIndex = group / 2;
      int pos = (group % 2) * 4;

      int part = (block[byteIndex] >>> (pos)) & 0x0F;
      int substituted = (invSbox) ? SerpentConstants.getInvSBoxValue(sBoxIndex, part) : SerpentConstants.getSBoxValue(sBoxIndex, part);
      block[byteIndex] = (byte) ((block[byteIndex] & ~(0x0F << pos)) | (substituted << pos));
    }
  }

  private byte[] linearTransformation(byte[] block) {
    int[] words = new int[4];
    for (int i = 0; i < 4; i++) {
      words[i] = bytesToInt(block, i * 4);
    }

    int x0 = words[0], x1 = words[1], x2 = words[2], x3 = words[3];
    x0 = Integer.rotateLeft(x0, 13);
    x2 = Integer.rotateLeft(x2, 3);
    x1 ^= x0 ^ x2;
    x3 ^= x2 ^ (x0 << 3);
    x1 = Integer.rotateLeft(x1, 1);
    x3 = Integer.rotateLeft(x3, 7);
    x0 ^= x1 ^ x3;
    x2 ^= x3 ^ (x1 << 7);
    x0 = Integer.rotateLeft(x0, 5);
    x2 = Integer.rotateLeft(x2, 22);

    return getBytes(x0, x1, x2, x3);
  }

  private byte[] inverseLinearTransformation(byte[] block) {
    int[] words = new int[4];
    for (int i = 0; i < 4; i++) {
      words[i] = bytesToInt(block, i * 4);
    }

    int x0 = words[0], x1 = words[1], x2 = words[2], x3 = words[3];
    x2 = Integer.rotateRight(x2, 22);
    x0 = Integer.rotateRight(x0, 5);
    x2 ^= x3 ^ (x1 << 7);
    x0 ^= x1 ^ x3;
    x3 = Integer.rotateRight(x3, 7);
    x1 = Integer.rotateRight(x1, 1);
    x3 ^= x2 ^ (x0 << 3);
    x1 ^= x0 ^ x2;
    x2 = Integer.rotateRight(x2, 3);
    x0 = Integer.rotateRight(x0, 13);

    return getBytes(x0, x1, x2, x3);
  }

  private byte[] getBytes(int x0, int x1, int x2, int x3) {
    byte[] result = new byte[16];
    System.arraycopy(intToBytes(x0), 0, result, 0, 4);
    System.arraycopy(intToBytes(x1), 0, result, 4, 4);
    System.arraycopy(intToBytes(x2), 0, result, 8, 4);
    System.arraycopy(intToBytes(x3), 0, result, 12, 4);
    return result;
  }

  private int bytesToInt(byte[] bytes, int offset) {
    return (bytes[offset] & 0xFF)
            | ((bytes[offset + 1] & 0xFF) << 8)
            | ((bytes[offset + 2] & 0xFF) << 16)
            | ((bytes[offset + 3] & 0xFF) << 24);
  }

  private byte[] intToBytes(int value) {
    return new byte[]{
            (byte) value,
            (byte) (value >>> 8),
            (byte) (value >>> 16),
            (byte) (value >>> 24)
    };
  }

  private void xor(byte[] a, byte[] b) {
    for (int i = 0; i < a.length; i++) {
      a[i] = (byte) (a[i] ^ b[i]);
    }
  }
}
