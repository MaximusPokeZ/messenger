package org.example.frontend.cipher.magenta;


import org.example.frontend.cipher.interfaces.EncryptorDecryptorSymmetric;
import org.example.frontend.cipher.interfaces.KeyExpansion;
import org.example.frontend.cipher.magenta.enums.MagentaKeyLength;
import org.example.frontend.cipher.magenta.supply.GeneratorSBlock;

public class Magenta implements EncryptorDecryptorSymmetric {

    private static final int BLOCK_SIZE = 16;
    private byte[] key = null;
    private final KeyExpansion keyExpansion;
    private byte[][] roundKeys = null;
    private final byte[] sBlock;

    public Magenta(MagentaKeyLength keyLength, byte[] key) {
        keyExpansion = new MagentaKeyExpansion();
        if (!checkAmountBytesInKey(key, keyLength)) {
            throw new IllegalArgumentException("Key is not a valid RC6 key");
        }
        setKey(key);
        GeneratorSBlock generatorSBlock = new GeneratorSBlock((byte) 0x65);
        sBlock = generatorSBlock.getSBlock();
    }

    @Override
    public void setKey(byte[] symmetricKey) {
        if (key != null && key.length != symmetricKey.length) {
            throw new IllegalArgumentException("Key length does not match symmetric key length");
        }
        this.key = symmetricKey;
        roundKeys = this.keyExpansion.generateRoundKeys(key);
    }

    private boolean checkAmountBytesInKey(byte[] key, MagentaKeyLength keyLength) {
        return key.length == keyLength.getKeyLengthInBytes();
    }

    @Override
    public byte[] encrypt(byte[] oneBlock) {
        if (roundKeys == null) {
            throw new IllegalArgumentException("Round keys not set");
        }
        int[] keyPattern = switch (roundKeys.length) {
            case 2 -> new int[]{0, 0, 1, 1, 0, 0};
            case 3 -> new int[]{0, 1, 2, 2, 1, 0};
            case 4 -> new int[]{0, 1, 2, 3, 3, 2, 1, 0};
            default -> throw new IllegalArgumentException("Unsupported key length");
        };
        byte[] result = oneBlock.clone();
        for (int j : keyPattern) {
            result = roundFunction(result, roundKeys[j]);
        }
        return result;
    }



    @Override
    public byte[] decrypt(byte[] oneBlock) {
        return v(encrypt(v(oneBlock)));
    }

    @Override
    public int getBlockSize() {
        return BLOCK_SIZE;
    }

    private byte[] v(byte[] oneBlock) {
        byte[][] splitted = splitArray(oneBlock);
        byte[] result = new byte[oneBlock.length];
        System.arraycopy(splitted[1], 0, result, 0, splitted[1].length);
        System.arraycopy(splitted[0], 0, result, splitted[1].length, splitted[0].length);
        return result;
    }


    private byte f(byte x) {
        return this.sBlock[Byte.toUnsignedInt(x)];
    }

    private byte a(byte x, byte y) {
        return f((byte) ( x ^ f(y) ) );
    }

    private byte[] pe(byte x, byte y) {
        byte[] result = new byte[2];
        result[0] = a(x,y);
        result[1] = a(y,x);
        return result;
    }

    private void pi(byte[] oneBlock) {
        byte[] clone = oneBlock.clone();
        for (int i = 0; i < 8; i++){
            byte[] resultPE = pe(clone[i], clone[i+8]);
            oneBlock[i] = resultPE[0];
            oneBlock[i+1] = resultPE[1];
        }
    }

    private byte[] t(byte[] oneBlock) {
        for (int i = 0; i < 4; i++){
            pi(oneBlock);
        }
        return oneBlock;
    }

    private byte[] evenByteArray(byte[] oneBlock) {
        byte[] even = new byte[8];
        for(int i=0,j = 0; i < 16; i += 2, j++) {
            even[j] = oneBlock[i];
        }
        return even;
    }

    private byte[] oddByteArray(byte[] oneBlock) {
        byte[] odd = new byte[8];
        for(int i=1,j = 0; i < 16; i += 2, j++) {
            odd[j] = oneBlock[i];
        }
        return odd;
    }

    private byte[] roundFunction(byte[] oneBlock, byte[] roundKey) {
        byte[][] splitted = splitArray(oneBlock);
        return concateArrays(splitted[1], xorByteArrays(splitted[0], e( concateArrays(splitted[1], roundKey))));
    }

    private byte[] e(byte[] oneBlock) {
        return evenByteArray(c(oneBlock, 3));
    }

    private byte[] c(byte[] oneBlock, int j) {
        if (j == 1) {
            return t(oneBlock);
        }
        byte[] left = new byte[oneBlock.length / 2];
        byte[] right = new byte[oneBlock.length / 2];
        System.arraycopy(oneBlock, 0, left, 0, left.length);
        System.arraycopy(oneBlock, left.length, right, 0, right.length);
        return t( concateArrays( xorByteArrays(left, evenByteArray(c(oneBlock, j - 1)) ),
                xorByteArrays(right, oddByteArray(c(oneBlock, j - 1))  ) ) );

    }

    private byte[] concateArrays(byte[] first, byte[] second) {
        byte[] result = new byte[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    private byte[][] splitArray(byte[] array) {
        if (array.length % 2 != 0) {
            throw new IllegalArgumentException("Array length should be even");
        }
        byte[][] result = new byte[2][];
        byte[] left = new byte[array.length / 2];
        byte[] right = new byte[array.length / 2];
        System.arraycopy(array, 0, left, 0, left.length);
        System.arraycopy(array, left.length, right, 0, right.length);
        result[0] = left;
        result[1] = right;
        return result;
    }

    private byte[] xorByteArrays(byte[] a, byte[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Arrays have different lengths");
        }
        byte[] result = new byte[a.length];
        for (int i = 0; i < a.length; ++i) {
            result[i] = (byte) (a[i] ^ b[i]);
        }
        return result;
    }










}
