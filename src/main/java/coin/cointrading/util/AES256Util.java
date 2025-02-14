package coin.cointrading.util;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class AES256Util {
    private static final String ALGORITHM = "AES";

    // AES-256 키 (Base64 디코딩 후 사용)
    @Value("${aes.secret.key}")
    private String secretKey;

    private byte[] KEY;

    // secretKey 주입 후 KEY 초기화
    @PostConstruct
    private void init() {
        if (secretKey != null && !secretKey.isEmpty()) {
            KEY = Base64.getDecoder().decode(secretKey);
        } else {
            throw new IllegalArgumentException("AES secret key must be provided");
        }
    }

    // 암호화
    public String encrypt(String data) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        SecretKeySpec secretKeySpec = new SecretKeySpec(KEY, ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);

        byte[] encryptedData = cipher.doFinal(data.getBytes());
        return Base64.getEncoder().encodeToString(encryptedData); // Base64 인코딩 후 반환
    }

    // 복호화
    public String decrypt(String encryptedData) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        SecretKeySpec secretKeySpec = new SecretKeySpec(KEY, ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);

        byte[] decodedBytes = Base64.getDecoder().decode(encryptedData);
        byte[] decryptedData = cipher.doFinal(decodedBytes);
        return new String(decryptedData);
    }
}
