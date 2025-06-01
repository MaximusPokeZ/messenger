package org.example.frontend.cipher.serpent;

public class PermuteBits {

  private PermuteBits() {}

  public static byte[] permute(byte[] input, int[] pBlock, boolean LSBorMSB, boolean startFromZeroIndx) {
    int totalBits = input.length * 8;

    int outputSizeBytes = (pBlock.length + 7) / 8;
    byte[] output = new byte[outputSizeBytes];

    for (int i = 0; i < pBlock.length; ++i) {
      int currBitPos = pBlock[i] - (startFromZeroIndx ? 1 : 0);

      if (currBitPos < 0 || currBitPos >= totalBits) {
        throw new IllegalArgumentException("Illegal bit position: " + currBitPos + " is out of range ["
                + (startFromZeroIndx ? 0 : 1) + ", " + (totalBits - (startFromZeroIndx ? 1 : 0)) + "]");
      }

      int currByteIndx = currBitPos / 8;
      int currBitIndx = (!LSBorMSB ? currBitPos % 8 : 7 - (currBitPos % 8));

      boolean bitValue = ((input[currByteIndx] >> (7 - currBitIndx)) & 1) == 1;

      int dstByteIndx = i / 8;
      int dstBitIndx = (!LSBorMSB ? 7 - (i % 8) : i % 8);

      if (bitValue) {
        output[dstByteIndx] |= (byte) (1 << dstBitIndx);
      }
    }

    return output;
  }
}
