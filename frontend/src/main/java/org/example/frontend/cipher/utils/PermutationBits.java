package org.example.frontend.cipher.utils;

import lombok.experimental.UtilityClass;

@UtilityClass
public class PermutationBits {

    public static byte[] permute(byte[] bytes, int[] pBlock, boolean isLeftToRight, boolean startsByOne) { // isLeftToRight: true - индексируемся слева направо от 0 до 63
                                                                                                        // false - справа налево от 0 до 63

        int amountNumbersInPBlock = pBlock.length;

        int outputAmountBytes = (amountNumbersInPBlock + 7)/ 8;

        byte[] result = new byte[outputAmountBytes];


        for (int i = 0; i < amountNumbersInPBlock; ++i) {

            int currentPositionBitInBytesArray = pBlock[i] + (startsByOne ? -1 : 0);

            int numberOfByteInArray = isLeftToRight ? currentPositionBitInBytesArray / 8 : bytes.length - 1 - currentPositionBitInBytesArray / 8;

            byte currentByte = bytes[numberOfByteInArray];

            int positionOfBitInByte = isLeftToRight ? 7 - currentPositionBitInBytesArray % 8 : currentPositionBitInBytesArray % 8;

            boolean isOne = (currentByte << 7 - positionOfBitInByte >> 7 & 1) == 1;

            int numberOfBytesInOutputArray = i / 8;

            if (isOne) {
                result[numberOfBytesInOutputArray] |= (byte) (1 << (7 - i % 8));
            }
        }

        return result;
    }


}
