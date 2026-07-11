package dev.deepdive.sandbox.crypto;

import java.security.GeneralSecurityException;
import java.security.Provider;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class AesUtilAfter {

    private static final String TRANSFORMATION = "AES/CBC/PKCS7Padding";
    private static final String KEY_ALGORITHM = "AES";

    private final Provider provider;

    public AesUtilAfter(Provider provider) {
        this.provider = Objects.requireNonNull(provider);
    }

    public byte[] encrypt(byte[] plainText, byte[] key, byte[] iv) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION, provider);
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, KEY_ALGORITHM), new IvParameterSpec(iv));
        return cipher.doFinal(plainText);
    }
}
