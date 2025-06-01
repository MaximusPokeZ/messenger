package org.example.frontend.cipher_ykwais.magenta.supply;


import static org.example.frontend.cipher_ykwais.utils.GaloisOperations.multOnX;

public class GeneratorSBlock {

    private byte poly;

    private byte[] sBlock = null;

    public GeneratorSBlock(byte poly) {
        this.poly = poly;
    }

    public void setPoly(byte poly) {
        this.poly = poly;
        sBlock = null;
    }


    public byte[] getSBlock() {
        return getSBlockInner().clone();
    }

    private byte[] getSBlockInner() {
        byte[] result = sBlock;
        if (result == null) {
            synchronized (this) {
                result = sBlock;
                if (result == null) {
                    sBlock = result = collectSBlock();
                }
            }
        }
        return result;
    }

    private byte[] collectSBlock() {
        byte[] sBox = new byte[256];
        sBox[0] = (byte) 0x01;
        for (int i = 1; i < 256; i++) {
            sBox[i] = multOnX(sBox[i - 1], poly);
        }
        sBox[sBox.length - 1] = (byte) 0x00;
        return sBox;
    }

}
