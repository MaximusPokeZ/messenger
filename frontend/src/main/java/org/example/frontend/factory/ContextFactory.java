package org.example.frontend.factory;

import lombok.extern.slf4j.Slf4j;
import org.example.frontend.cipher_ykwais.constants.CipherMode;
import org.example.frontend.cipher_ykwais.constants.PaddingMode;
import org.example.frontend.cipher_ykwais.context.Context;
import org.example.frontend.cipher_ykwais.interfaces.EncryptorDecryptorSymmetric;
import org.example.frontend.cipher_ykwais.magenta.Magenta;
import org.example.frontend.cipher_ykwais.magenta.enums.MagentaKeyLength;
import org.example.frontend.cipher_ykwais.rc6.RC6;
import org.example.frontend.cipher_ykwais.rc6.enums.RC6KeyLength;
import org.example.frontend.cipher_ykwais.serpent.Serpent;
import org.example.frontend.cipher_ykwais.serpent.SerpentConfiguration;
import org.example.frontend.manager.DiffieHellmanManager;
import org.example.frontend.model.main.ChatRoom;
import org.example.frontend.utils.DiffieHellman;

@Slf4j
public class ContextFactory {
    private ContextFactory() {}
    public static Context getContext(ChatRoom room) {
        log.info("content of current room: {}", room);
        CipherMode currentCipherMpde =
                switch (room.getCipherMode()) {
                    case "ECB" -> CipherMode.ECB;
                    case "CBC" -> CipherMode.CBC;
                    case "OFB" -> CipherMode.OFB;
                    case "CTR" -> CipherMode.CTR;
                    case "PCBC" -> CipherMode.PCBC;
                    case "RANDOM_DELTA" -> CipherMode.RD;
                    case "CFB" -> CipherMode.CFB;
                    default -> throw new IllegalStateException("Unexpected value: " + room.getCipherMode());
                };

        PaddingMode currentPaddingMode =
                switch(room.getPaddingMode()) {
                    case "ZEROS" -> PaddingMode.ZEROS;
                    case "ANSI_X923" -> PaddingMode.ANSI_X923;
                    case "PKCS7" -> PaddingMode.PKCS7;
                    case "ISO_10126" -> PaddingMode.ISO_10126;
                    default -> throw new IllegalArgumentException("Unexpected value: " + room.getPaddingMode());
                };

        DiffieHellman dh = DiffieHellmanManager.get(room.getRoomId());

        byte[] fullKey = dh.getSharedSecret().toByteArray();
        int requiredLength = switch (room.getKeyBitLength()) {//сюда приходит null блять
            case "128" -> 16;
            case "192" -> 24;
            case "256" -> 32;
            default -> throw new IllegalArgumentException("Unexpected key length: " + room.getKeyBitLength());
        };


        byte[] key = new byte[requiredLength];
        int copyLength = Math.min(fullKey.length, requiredLength);
        int offset = fullKey.length > requiredLength ? fullKey.length - requiredLength : 0;
        System.arraycopy(fullKey, offset, key, requiredLength - copyLength, copyLength);


        EncryptorDecryptorSymmetric algo =
                switch (room.getCipher()) {
                    case "RC6" -> {

                        RC6KeyLength rc6KeyLength =
                                switch (room.getKeyBitLength()) {
                                    case "128" -> RC6KeyLength.KEY_128;
                                    case "192" -> RC6KeyLength.KEY_192;
                                    case "256" -> RC6KeyLength.KEY_256;
                                    default -> throw new IllegalArgumentException("Unexpected key length: " + room.getKeyBitLength());
                                };

                        yield new RC6(rc6KeyLength, key);

                    }
                    case "MAGENTA" -> {
                        MagentaKeyLength magentaKeyLength =
                                switch(room.getKeyBitLength()) {
                                    case "128" -> MagentaKeyLength.KEY_128;
                                    case "192" -> MagentaKeyLength.KEY_192;
                                    case "256" -> MagentaKeyLength.KEY_256;
                                    default -> throw new IllegalArgumentException("Unexpected key length: " + room.getKeyBitLength());
                                };
                        yield new Magenta(magentaKeyLength, key);
                    }
                    case "SERPENT" -> {
                        int keyLength =
                                switch (room.getKeyBitLength()) {
                                    case "128" -> 128;
                                    case "192" -> 192;
                                    case "256" -> 256;
                                    default -> throw new IllegalArgumentException("Unexpected key length: " + room.getKeyBitLength());
                                };
                        SerpentConfiguration serpentConfiguration = new SerpentConfiguration(keyLength);
                        Serpent serpent = new Serpent(serpentConfiguration);
                        serpent.setKey(key);
                        yield serpent;
                    }
                    default -> throw new IllegalArgumentException("Unexpected value: " + room.getCipher());
                };

        return new Context(algo, currentCipherMpde, currentPaddingMode, room.getIv().getBytes());
    }
}
