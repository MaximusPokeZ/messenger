package org.example.frontend.cipher.interfaces;

public interface KeyExpansion {
    byte[][] generateRoundKeys(byte[] key);
}
