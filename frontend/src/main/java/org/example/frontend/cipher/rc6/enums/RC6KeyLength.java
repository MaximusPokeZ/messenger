package org.example.frontend.cipher.rc6.enums;

public enum RC6KeyLength {
    KEY_128(16),
    KEY_192(24),
    KEY_256(32);

    private final int keyLengthInBytes;

    RC6KeyLength(int keyLengthInBytes) {
        this.keyLengthInBytes = keyLengthInBytes;
    }
    public int getKeyLengthInBytes() {
        return keyLengthInBytes;
    }

}
