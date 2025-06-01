package org.example.frontend.cipher.rc6;

import lombok.extern.slf4j.Slf4j;
import org.example.frontend.cipher.interfaces.KeyExpansion;


import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@Slf4j
public class RC6KeyExpansionImpl implements KeyExpansion {

    private static final int AMOUNT_ROUNDS = 20;
    private static final int W = 32;
    private static final int P = 0xB7E15163;
    private static final int Q = 0x9E3779B9;


    @Override
    public byte[][] generateRoundKeys(byte[] key) {

        int b = key.length;
        int c = (b == 0) ? 1 : ((8 * b ) / W);

        int[] l = new int[c];

        ByteBuffer buffer = ByteBuffer.wrap(key);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        for (int i = 0; i < l.length; i++) {
            l[i] = buffer.getInt();
        }

        int t = 2 * AMOUNT_ROUNDS + 4;
        int[] s = new int[t];
        s[0] = P;
        for (int i = 1; i < s.length; i++) {
            s[i] = (int) (Integer.toUnsignedLong(s[i - 1]) + Integer.toUnsignedLong(Q));
        }

        int aWord = 0;
        int bWord = 0;
        int i = 0;
        int j = 0;
        int v = Math.max(t, c) * 3;
        for (int k = 0; k < v; k++) {
            aWord = s[i] = leftShift((int) (Integer.toUnsignedLong(s[i]) + Integer.toUnsignedLong(aWord)  + Integer.toUnsignedLong(bWord)), 3);
            bWord = l[j] = leftShift((int) (Integer.toUnsignedLong(l[j]) + Integer.toUnsignedLong(aWord)  + Integer.toUnsignedLong(bWord)), (int) (Integer.toUnsignedLong(aWord)  + Integer.toUnsignedLong(bWord)));
            i = (int)((Integer.toUnsignedLong(i) + 1) % Integer.toUnsignedLong(t));
            j = (int)((Integer.toUnsignedLong(j) + 1) % Integer.toUnsignedLong(c));
        }

        byte[][] result = new byte[s.length][4];
        ByteBuffer bufferTmp = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);

        for (int k = 0; k < s.length; k++) {
            bufferTmp.putInt(s[k]);
            result[k] = bufferTmp.array().clone();
            bufferTmp.clear();
        }

        return result;
    }

    private int leftShift(int x, int shift) {
        return (x << shift) | ( x >>> (32 - shift));
    }
}
