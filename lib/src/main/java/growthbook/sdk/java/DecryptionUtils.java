package growthbook.sdk.java;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * INTERNAL: This class is used internally to decrypt an encrypted features response
 */
@Slf4j
class DecryptionUtils {

    public static class DecryptionException extends Exception {
        public DecryptionException(String errorMessage) {
            super(errorMessage);
        }
    }

    public static String decrypt(String payload, String encryptionKey) throws DecryptionException {
        if (!payload.contains(".")) {
            throw new DecryptionException("Invalid payload");
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

            // This decoder ensures no malformed input due to using a mismatching iv key
            StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(plainText));

            return new String(plainText);
        } catch (InvalidAlgorithmParameterException e) {
            throw new DecryptionException("Invalid payload");
        } catch (InvalidKeyException e) {
            throw new DecryptionException("Invalid encryption key");
        } catch (
            NoSuchAlgorithmException
            | NoSuchPaddingException
            | IllegalBlockSizeException
            | CharacterCodingException
            | IllegalArgumentException
            | BadPaddingException e
        ) {
            log.error("Unable to decrypt payload", e);
            throw new DecryptionException(e.getMessage());
        }
    }


    private static SecretKeySpec keyFromSecret(String encryptionKey) {
        byte[] encodedKeyBytes = encryptionKey.getBytes(StandardCharsets.UTF_8);
        byte[] keyBytes = Base64.getDecoder().decode(encodedKeyBytes);
        return new SecretKeySpec(keyBytes, "AES");
    }
}
