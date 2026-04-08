package br.com.holding.payments.company;

import lombok.RequiredArgsConstructor;
import org.jasypt.encryption.StringEncryptor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EncryptionService {

    private final StringEncryptor jasyptStringEncryptor;

    public String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return null;
        }
        return jasyptStringEncryptor.encrypt(plainText);
    }

    public String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isBlank()) {
            return null;
        }
        return jasyptStringEncryptor.decrypt(encryptedText);
    }
}
