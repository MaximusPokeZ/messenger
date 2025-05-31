package org.example.frontend.cipher_ykwais.rc6;

import lombok.extern.slf4j.Slf4j;
import org.example.frontend.cipher_ykwais.interfaces.EncryptorDecryptorSymmetric;
import org.example.frontend.cipher_ykwais.interfaces.KeyExpansion;
import org.example.frontend.cipher_ykwais.rc6.enums.RC6KeyLength;


import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@Slf4j
public class RC6 implements EncryptorDecryptorSymmetric {

    private static final int BLOCK_SIZE = 16;
    private byte[] key = null;
    private final KeyExpansion keyExpansion;
    int[] s = null;

    public RC6(RC6KeyLength rc6KeyLength, byte[] key) {
        keyExpansion = new RC6KeyExpansionImpl();
        if (!checkAmountBytesInKey(key, rc6KeyLength)) {
            throw new IllegalArgumentException("Key is not a valid RC6 key");
        }
        setKey(key);
    }

    private boolean checkAmountBytesInKey(byte[] key, RC6KeyLength keyLength) {
        return key.length == keyLength.getKeyLengthInBytes();
    }


    @Override
    public void setKey(byte[] symmetricKey) {
        if (key != null && key.length != symmetricKey.length) {
            throw new IllegalArgumentException("Key length does not match symmetric key length");
        }
        this.key = symmetricKey;

        byte[][] roundKeys = this.keyExpansion.generateRoundKeys(this.key);
        ByteBuffer buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        s = new int[roundKeys.length];

        for (int i = 0; i < roundKeys.length; i++) {
            buffer.put(roundKeys[i]);
            buffer.flip();
            s[i] = buffer.getInt();
            buffer.clear();
        }
    }

    @Override
    public byte[] encrypt(byte[] oneBlock) {
        return encryptDecryptInner(oneBlock, true);
    }

    @Override
    public byte[] decrypt(byte[] oneBlock) {
        return encryptDecryptInner(oneBlock, false);
    }

    private byte[] encryptDecryptInner(byte[] oneBlock, boolean isEncrypt) {
        ByteBuffer buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);

        byte[] workingBlock = oneBlock.clone();

        int[] parts = new int[4];

        for (int i = 0; i < 4; i++) {
            byte[] oneWord = new byte[BLOCK_SIZE / 4];
            System.arraycopy(workingBlock, i*4, oneWord, 0, oneWord.length);
            buffer.put(oneWord);
            buffer.flip();
            parts[i] = buffer.getInt();
            buffer.clear();
        }

        int a = parts[0];
        int b = parts[1];
        int c = parts[2];
        int d = parts[3];

        if (isEncrypt) {
            //pre-whitening
            b =(int) (Integer.toUnsignedLong(b) + Integer.toUnsignedLong(s[0]));
            d = (int) (Integer.toUnsignedLong(d) + Integer.toUnsignedLong(s[1]));

            int amountRounds = (s.length - 4) / 2;

            for (int i = 1; i <= amountRounds; i++) {
                int t = leftShift( (int) (Integer.toUnsignedLong(b) * (Integer.toUnsignedLong(b) * 2  + 1 )), 5 );
                int u = leftShift((int) (Integer.toUnsignedLong(d) * (2 * Integer.toUnsignedLong(d) + 1)),5);
                a = (int) (Integer.toUnsignedLong( leftShift((int) (Integer.toUnsignedLong(a) ^ Integer.toUnsignedLong(t)), u) ) + Integer.toUnsignedLong(s[2*i]));
                c = (int) (Integer.toUnsignedLong( leftShift((int) (Integer.toUnsignedLong(c) ^ Integer.toUnsignedLong(u)), t) ) + Integer.toUnsignedLong(s[2*i + 1]));

                int tmp = a;
                a = b;
                b = c;
                c = d;
                d = tmp;
            }

            //post-whitening
            a = (int) ( Integer.toUnsignedLong(a) + Integer.toUnsignedLong(s[2*amountRounds + 2]));
            c = (int) (Integer.toUnsignedLong(c) + Integer.toUnsignedLong(s[2*amountRounds + 3]));
        } else {
            int amountRounds = (s.length - 4) / 2;
            //pre-whitening reverse
            c  =(int) (Integer.toUnsignedLong(c) - Integer.toUnsignedLong(s[2 * amountRounds + 3]));
            a = (int) (Integer.toUnsignedLong(a) - Integer.toUnsignedLong(s[2*amountRounds + 2]));



            for (int i = amountRounds; i >= 1; i--) {

                int tmp = d;
                d = c;
                c = b;
                b = a;
                a = tmp;

                int t = leftShift( (int) (Integer.toUnsignedLong(b) * (Integer.toUnsignedLong(b) * 2  + 1 )), 5 );
                int u = leftShift((int) (Integer.toUnsignedLong(d) * (2 * Integer.toUnsignedLong(d) + 1)),5);

                c = (int) ( Integer.toUnsignedLong(rightShift( (int)(Integer.toUnsignedLong(c) - Integer.toUnsignedLong(s[2*i + 1]) ), t) ) ^ Integer.toUnsignedLong(u));
                a = (int) ( Integer.toUnsignedLong(rightShift( (int)(Integer.toUnsignedLong(a) - Integer.toUnsignedLong(s[2*i])),u)) ^ Integer.toUnsignedLong(t));
            }

            //post-whitening
            d = (int) ( Integer.toUnsignedLong(d) - Integer.toUnsignedLong(s[1]));
            b = (int) (Integer.toUnsignedLong(b) - Integer.toUnsignedLong(s[0]));
        }

        parts[0] = a;
        parts[1] = b;
        parts[2] = c;
        parts[3] = d;

        for (int i = 0; i < 4; i++) {
            buffer.putInt(parts[i]);
            System.arraycopy(buffer.array(), 0, workingBlock, i*4, buffer.array().length);
            buffer.clear();
        }
        return workingBlock;


    }

    @Override
    public int getBlockSize() {
        return BLOCK_SIZE;
    }


    private int leftShift(int x, int shift) {
        return (x << shift) | ( x >>> (32 - shift));
    }

    private int rightShift(int x, int shift) {
        return (x >>> shift) | ( x << (32 - shift));
    }


}
