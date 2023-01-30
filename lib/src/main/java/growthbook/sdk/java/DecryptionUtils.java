package growthbook.sdk.java;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class DecryptionUtils {
    public static String decrypt(String payload, String encryptionKey) {
        if (!payload.contains(".")) {
            throw new IllegalArgumentException("Invalid payload");
        }

        try {
            String[] parts = payload.split("\\.");

            String iv = parts[0];
            String cipherText = parts[1];

            byte[] decodedIv = Base64.getDecoder().decode(iv.getBytes(StandardCharsets.UTF_8));
            IvParameterSpec ivParameterSpec = new IvParameterSpec(decodedIv);

            Cipher cipher = Cipher.getInstance("AES/CBC/NOPADDING");
            cipher.init(Cipher.DECRYPT_MODE, DecryptionUtils.keyFromSecret(encryptionKey), ivParameterSpec);

            byte[] decodedCipher = Base64.getDecoder().decode(cipherText.getBytes(StandardCharsets.UTF_8));
            byte[] plainText = cipher.doFinal(decodedCipher);

            return new String(plainText);
        } catch (InvalidAlgorithmParameterException e) {
            throw new IllegalArgumentException("Invalid payload");
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException("Invalid encryption key");
        } catch (
                NoSuchAlgorithmException
                | NoSuchPaddingException
                | IllegalBlockSizeException
                | BadPaddingException e
        ) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }


    private static SecretKeySpec keyFromSecret(String encryptionKey) {
        byte[] encodedKeyBytes = encryptionKey.getBytes(StandardCharsets.UTF_8);
        byte[] keyBytes = Base64.getDecoder().decode(encodedKeyBytes);
        return new SecretKeySpec(keyBytes, "AES");
    }
}
