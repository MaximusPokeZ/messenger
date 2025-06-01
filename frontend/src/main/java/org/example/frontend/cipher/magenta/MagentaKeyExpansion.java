package org.example.frontend.cipher.magenta;


import org.example.frontend.cipher.interfaces.KeyExpansion;

public class MagentaKeyExpansion implements KeyExpansion {

    @Override
    public byte[][] generateRoundKeys(byte[] key) {

        int amountRoundKeys = key.length / 8;

        byte[][] roundKeys = new byte[amountRoundKeys][];

        for (int i = 0; i < amountRoundKeys; i++) {
            roundKeys[i] = new byte[8];
            System.arraycopy(key, i * 8, roundKeys[i], 0, 8);
        }

        return roundKeys;
    }
}
