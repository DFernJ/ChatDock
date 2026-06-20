package com.DockerOps.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import com.DockerOps.exception.EncryptionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Slf4j
@Service
public class AesGcmEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_BYTES = 12;
    private static final int KEY_BYTES = 32; // 256 bits

    @Value("${app.security.cypher.encryption-key}")
    private String encryptionMasterKey;

    private SecretKey secretKey;
    private SecureRandom secureRandom;

    @PostConstruct
    public void init() {
        byte[] keyBytes = Base64.getDecoder().decode(encryptionMasterKey);
        if (keyBytes.length != KEY_BYTES) {
            throw new IllegalStateException("Encryption key must be 256 bits (32 bytes) when Base64 decoded. Now is: " + keyBytes.length);
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
        this.secureRandom = new SecureRandom();
    }

    public String encrypt(String plainText){
        if (plainText == null) return null;
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new EncryptionException("Error during encryption");
        }
    }

    public String decrypt(String cipherText) {
        if (cipherText == null) return null;
        try {
            byte[] combined = Base64.getDecoder().decode(cipherText);

            if (combined.length <= IV_BYTES) {
                throw new IllegalArgumentException("Invalid cipher data, so tiny.");
            }

            byte[] iv = Arrays.copyOfRange(combined, 0, IV_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(combined, IV_BYTES, combined.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);

            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new EncryptionException("Error during decryption");
        }
    }
}
