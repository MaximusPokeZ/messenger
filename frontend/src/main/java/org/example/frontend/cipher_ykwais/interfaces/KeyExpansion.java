package org.example.frontend.cipher_ykwais.interfaces;

public interface KeyExpansion {
    byte[][] generateRoundKeys(byte[] key);
}
