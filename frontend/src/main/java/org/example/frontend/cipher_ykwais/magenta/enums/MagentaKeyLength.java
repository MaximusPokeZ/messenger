package org.example.frontend.cipher_ykwais.magenta.enums;

public enum MagentaKeyLength {
    KEY_128(16),
    KEY_192(24),
    KEY_256(32);

    private final int keyLengthInBytes;

    MagentaKeyLength(int keyLengthInBytes) {
        this.keyLengthInBytes = keyLengthInBytes;
    }
    public int getKeyLengthInBytes() {
        return keyLengthInBytes;
    }
}
