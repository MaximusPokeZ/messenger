package org.example.frontend.cipher.interfaces;

public interface EncryptorDecryptorSymmetric {
    void setKey(byte[] symmetricKey);

    byte[] encrypt(byte[] message);

    byte[] decrypt(byte[] cipherText);

    int getBlockSize();
}
