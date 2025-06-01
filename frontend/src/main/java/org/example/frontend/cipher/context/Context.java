package org.example.frontend.cipher.context;

import javafx.util.Pair;
import lombok.extern.slf4j.Slf4j;
import org.example.frontend.cipher.constants.CipherMode;
import org.example.frontend.cipher.constants.PaddingMode;
import org.example.frontend.cipher.interfaces.EncryptorDecryptorSymmetric;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.stream.IntStream;


@Slf4j
public class Context {

    private final CipherMode cipherMode;
    private final PaddingMode paddingMode;
    private final EncryptorDecryptorSymmetric encryptorDecryptorSymmetric;
    private static final int BUFFER_SIZE = 1024 * 512;
    private byte[] initialVector;
    private Integer deltaForRD = null;
    private final int blockSize;

    public Context(EncryptorDecryptorSymmetric algorithm, CipherMode cipherMode, PaddingMode paddingMode, byte[] initializationVector, Object... extras) {
        this.initialVector = initializationVector;
        this.cipherMode = cipherMode;
        this.paddingMode = paddingMode;
        this.encryptorDecryptorSymmetric = algorithm;



        if (extras != null && extras.length > 0) {
            if (!(extras[0] instanceof Integer)) {
                throw new IllegalArgumentException("First extra parameter must be Integer");
            }

            this.deltaForRD = (Integer) extras[0];

        }

        this.blockSize = encryptorDecryptorSymmetric.getBlockSize();
    }

    public void encrypt(Path inputPath, Path encryptedPath) throws IOException {
        byte[] previous = null;
        try (InputStream in = Files.newInputStream(inputPath);
             OutputStream out = Files.newOutputStream(encryptedPath)) {

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            byte[] lastBlock = null;

            while ((bytesRead = in.read(buffer)) != -1) {
                if (lastBlock != null) {
                    Pair<byte[], byte[]> encryptedPart = encryptDecryptInner(lastBlock, previous,true);
                    previous = encryptedPart.getValue();
                    out.write(encryptedPart.getKey());
                }
                lastBlock = Arrays.copyOf(buffer, bytesRead);
            }

            if (lastBlock != null) {
                byte[] paddedBlock = addPadding(lastBlock);
                Pair<byte[], byte[]> encryptedPart = encryptDecryptInner(paddedBlock, previous,true);
                out.write(encryptedPart.getKey());
            } else {
                byte[] emptyPadded = addPadding(new byte[0]);
                Pair<byte[], byte[]> encryptedPart = encryptDecryptInner(emptyPadded, previous,true);
                out.write(encryptedPart.getKey());
            }
        }
    }

    public void decrypt(Path encryptedFilePath, Path decryptedFilePath) throws IOException {
        byte[] previous = null;
        try (InputStream in = Files.newInputStream(encryptedFilePath);
             OutputStream out = Files.newOutputStream(decryptedFilePath)) {

            byte[] buffer = new byte[BUFFER_SIZE];
            byte[] lastBlock = null;
            int bytesRead;

            while ((bytesRead = in.read(buffer)) != -1) {
                if (lastBlock != null) {
                    out.write(lastBlock);
                }
                Pair<byte[], byte[]> afterDecrypt = encryptDecryptInner(Arrays.copyOf(buffer, bytesRead), previous, false);
                lastBlock = afterDecrypt.getKey();
                previous = afterDecrypt.getValue();
            }

            if (lastBlock != null) {
                byte[] unpadded = removePadding(lastBlock);
                out.write(unpadded);
            }
        }
    }

    public Pair<byte[], byte[]> encryptDecryptInner(byte[] data, byte[] prev, boolean isEncrypt) {
        if (initialVector != null && initialVector.length != blockSize) {
            throw new IllegalArgumentException("Initial vector length does not match block size");
        }

        if (initialVector == null ) {
            initialVector = new byte[blockSize];
            SecureRandom secureRandom = new SecureRandom();
            secureRandom.nextBytes(initialVector);
        }

        byte[] result = new byte[data.length];
        byte[] previous = new byte[blockSize];
        int amountBlock = data.length / blockSize;

        switch(cipherMode) {
            case ECB:
                IntStream.range(0, amountBlock).parallel().forEach(i -> {
                    int offset = i * blockSize;
                    byte[] block = Arrays.copyOfRange(data, offset, offset + blockSize);
                    byte[] preResult = isEncrypt ? encryptorDecryptorSymmetric.encrypt(block) : encryptorDecryptorSymmetric.decrypt(block);
                    System.arraycopy(preResult, 0, result, offset, blockSize);
                });
                break;
            case CBC:
                if (isEncrypt) {
                    byte[] blockMessage = Arrays.copyOfRange(data,0, blockSize );
                    blockMessage = xorByteArrays(blockMessage, (prev == null) ? initialVector : prev);
                    byte[] resultPrev = encryptorDecryptorSymmetric.encrypt(blockMessage);
                    System.arraycopy(resultPrev, 0, result, 0, blockSize);
                    for(int i = blockSize; i < data.length; i += blockSize) {
                        blockMessage = Arrays.copyOfRange(data, i, i + blockSize);
                        blockMessage = xorByteArrays(blockMessage, resultPrev);
                        resultPrev = encryptorDecryptorSymmetric.encrypt(blockMessage);
                        System.arraycopy(resultPrev, 0, result, i, blockSize);
                    }
                    previous = resultPrev;
                } else {
                    byte[] decodedBlocks = new byte[data.length];
                    IntStream.range(0, amountBlock).parallel().forEach(i -> {
                        int offset = i * blockSize;
                        byte[] block = Arrays.copyOfRange(data, offset, offset + blockSize);
                        byte[] preResult = encryptorDecryptorSymmetric.decrypt(block);
                        System.arraycopy(preResult, 0, decodedBlocks, offset, blockSize);
                    });

                    byte[] arrayCipherTexts = new byte[data.length + blockSize];//тут размер вектора инициализации добавил
                    System.arraycopy((prev == null) ? initialVector : prev, 0, arrayCipherTexts, 0, initialVector.length);
                    System.arraycopy(data, 0, arrayCipherTexts, initialVector.length, data.length);

                    IntStream.range(0, amountBlock).parallel().forEach(i -> {
                        int offset = i * blockSize;
                        byte[] firstBlock = Arrays.copyOfRange(arrayCipherTexts, offset, offset + blockSize);
                        byte[] secondBlock = Arrays.copyOfRange(decodedBlocks, offset, offset + blockSize);
                        byte[] message = xorByteArrays(firstBlock, secondBlock);
                        System.arraycopy(message, 0, result, offset, blockSize);
                    });

                    System.arraycopy(arrayCipherTexts, arrayCipherTexts.length - blockSize, previous, 0, blockSize);
                }
                break;
            case OFB:
                byte[] previousEncryptedBlockOFB = (prev == null) ? encryptorDecryptorSymmetric.encrypt(initialVector) : prev;
                for(int i = 0; i < amountBlock; i++) {
                    int offset = i * blockSize;
                    byte[] block = Arrays.copyOfRange(data, offset, offset + blockSize);
                    byte[] preResult = xorByteArrays(previousEncryptedBlockOFB, block);
                    System.arraycopy(preResult, 0, result, offset, blockSize);
                    previousEncryptedBlockOFB = encryptorDecryptorSymmetric.encrypt(previousEncryptedBlockOFB);
                }
                previous = previousEncryptedBlockOFB;
                break;
            case CFB:
                if (isEncrypt) {
                    byte[] previousEncryptedBlockCFB = (prev == null) ? initialVector : prev;
                    for(int i = 0; i < amountBlock; i++) {
                        int offset = i * blockSize;
                        byte[] block = Arrays.copyOfRange(data, offset, offset + blockSize);
                        byte[] preResult = xorByteArrays(encryptorDecryptorSymmetric.encrypt(previousEncryptedBlockCFB), block);
                        System.arraycopy(preResult, 0, result, offset, blockSize);
                        previousEncryptedBlockCFB = preResult;
                        previous = previousEncryptedBlockCFB;
                    }
                } else {
                    byte[] cipherTextsArray = new byte[data.length + initialVector.length];
                    System.arraycopy((prev == null) ? initialVector : prev, 0, cipherTextsArray, 0, initialVector.length);
                    System.arraycopy(data, 0, cipherTextsArray, initialVector.length, data.length);

                    IntStream.range(0, amountBlock).parallel().forEach(i -> {
                        int offset = i * blockSize;
                        byte[] blockFirst = Arrays.copyOfRange(cipherTextsArray, offset, offset + blockSize);
                        byte[] blockSecond = Arrays.copyOfRange(cipherTextsArray, offset+blockSize, offset + blockSize * 2);
                        byte[] message = xorByteArrays(encryptorDecryptorSymmetric.encrypt(blockFirst), blockSecond);
                        System.arraycopy(message, 0, result, offset, blockSize);
                    });
                    System.arraycopy(data, data.length - blockSize, previous, 0 , blockSize);
                }
                break;
            case PCBC:
                byte[] previousEncryptedBlockPCBC = (prev == null) ? initialVector : prev;
                for(int i = 0; i < amountBlock; i++) {
                    int offset = i * blockSize;
                    byte[] block = Arrays.copyOfRange(data, offset, offset + blockSize);
                    byte[] xored = xorByteArrays(previousEncryptedBlockPCBC, isEncrypt ? block : encryptorDecryptorSymmetric.decrypt(block)) ;
                    byte[] preResult = isEncrypt ? encryptorDecryptorSymmetric.encrypt(xored) : xored;
                    System.arraycopy(preResult, 0, result, offset, blockSize);
                    previousEncryptedBlockPCBC = xorByteArrays(preResult, block);
                }
                previous = previousEncryptedBlockPCBC;
                break;
            case CTR, RD:
                byte[] iv = new byte[initialVector.length];
                System.arraycopy(initialVector, 0, iv, 0, initialVector.length);
                BigInteger initialCounter = (prev == null) ? new BigInteger(iv) : new BigInteger(prev);


                if (cipherMode == CipherMode.RD && deltaForRD == null) {
                    throw new IllegalArgumentException("deltaForRD is null!!! need to add for constructor of Context");
                }

                if (cipherMode == CipherMode.RD && deltaForRD < 0) {
                    throw  new IllegalArgumentException("delta can't be negative!");
                }

                int delta = cipherMode == CipherMode.CTR ? 1 : deltaForRD;
                IntStream.range(0, amountBlock).parallel().forEach(i -> {
                    int offset = i * blockSize;
                    byte[] block = Arrays.copyOfRange(data, offset, offset + blockSize);
                    BigInteger currentCounter = initialCounter;
                    BigInteger iNumber = BigInteger.valueOf(i);
                    BigInteger deltaBigInteger = BigInteger.valueOf(delta);
                    currentCounter = currentCounter.add(deltaBigInteger.multiply(iNumber));
                    byte[] bytesOfCurrentCounter = currentCounter.toByteArray();
                    bytesOfCurrentCounter = Arrays.copyOf(bytesOfCurrentCounter, blockSize);
                    byte[] xored = xorByteArrays(bytesOfCurrentCounter, block);
                    System.arraycopy(xored, 0, result, offset, blockSize);
                });
                previous = initialCounter.add(BigInteger.valueOf(amountBlock).multiply(BigInteger.valueOf(delta))).toByteArray();
                break;
            default:
                throw new UnsupportedOperationException("Unsupported cipher mode: " + cipherMode);
        }

        return new Pair<>(result, previous);
    }


    public byte[] addPadding(byte[] data) {
        switch (paddingMode) {
            case ZEROS -> { return addZerosPadding(data); }
            case PKCS7 -> { return addPkcs7Padding(data); }
            case ANSI_X923 -> { return addAnsiX923Padding(data); }
            case ISO_10126 -> { return addIso10126Padding(data); }
            default -> { return data; }
        }
    }

    public byte[] removePadding(byte[] data) throws IllegalArgumentException {
        switch (paddingMode) {
            case ZEROS -> {return removeZerosPadding(data); }
            case PKCS7 -> { return removePkcs7Padding(data); }
            case ISO_10126 -> { return removeIso10126Padding(data); }
            case ANSI_X923 -> { return removeAnsiX923Padding(data); }
            default -> { return data; }
        }
    }


    public byte[] addIso10126Padding(byte[] data) {
        int paddingLength = blockSize - (data.length % blockSize);
        byte[] paddedData = new byte[data.length + paddingLength];
        System.arraycopy(data, 0, paddedData, 0, data.length);
        SecureRandom random = new SecureRandom();
        for (int i = data.length; i < paddedData.length-1; i++) {
            paddedData[i] = (byte) random.nextInt(256);
        }
        paddedData[paddedData.length-1] = (byte) paddingLength;
        return paddedData;
    }

    public byte[] removeIso10126Padding(byte[] data) {
        return removePkcs7Padding(data);
    }


    public byte[] addAnsiX923Padding(byte[] data) {
        int paddingLength = blockSize - (data.length % blockSize);
        byte[] paddedData = new byte[data.length + paddingLength];
        System.arraycopy(data, 0, paddedData, 0, data.length);
        paddedData[paddedData.length-1] = (byte) paddingLength;
        return paddedData;
    }

    public byte[] removeAnsiX923Padding(byte[] data) {
        return removePkcs7Padding(data);
    }


    public byte[] addPkcs7Padding(byte[] data) {
        int paddingLength = blockSize - (data.length % blockSize);
        byte[] paddedArray = new byte[data.length + paddingLength];
        System.arraycopy(data, 0, paddedArray, 0, data.length);
        for (int i = data.length; i < paddedArray.length; i++) {
            paddedArray[i] = (byte) paddingLength;
        }
        return paddedArray;
    }

    public byte[] removePkcs7Padding(byte[] data) {
        int paddingLength = data[data.length - 1];
        if (paddingLength > blockSize || paddingLength < 0) {
            throw new IllegalArgumentException("Invalid padding length: " + paddingLength);
        }
        byte[] unpadded = new byte[data.length - paddingLength];
        System.arraycopy(data, 0, unpadded, 0, unpadded.length);
        return unpadded;
    }

    public byte[] addZerosPadding(byte[] data) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("data is null or empty");
        }
        int paddingLength = ( blockSize - (data.length % blockSize)) % blockSize;
        byte[] paddedArray = new byte[data.length + paddingLength];
        System.arraycopy(data, 0, paddedArray, 0, data.length);
        return paddedArray;
    }

    public byte[] removeZerosPadding(byte[] data) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("data is null or empty");
        }
        int length = data.length;
        for (int i = 1; i < blockSize; ++i) {
            if (data[data.length - i] == 0) {
                length--;
            } else {
                break;
            }
        }
        byte[] unpadded = new byte[length];
        System.arraycopy(data, 0, unpadded, 0, unpadded.length);
        return unpadded;
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
