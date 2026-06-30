package dev.deepdive.sandbox.crypto;

import java.security.GeneralSecurityException;
import java.security.Security;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

public final class AesUtilBefore {

    private static final String TRANSFORMATION = "AES/CBC/PKCS7Padding";
    private static final String KEY_ALGORITHM = "AES";

    private AesUtilBefore() {
    }

    public static byte[] encrypt(byte[] plainText, byte[] key, byte[] iv) throws GeneralSecurityException {
        Security.addProvider(new BouncyCastleProvider());

        Cipher cipher = Cipher.getInstance(TRANSFORMATION, BouncyCastleProvider.PROVIDER_NAME);
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, KEY_ALGORITHM), new IvParameterSpec(iv));
        return cipher.doFinal(plainText);
    }
}
