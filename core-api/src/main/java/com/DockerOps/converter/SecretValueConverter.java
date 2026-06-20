package com.DockerOps.converter;

import com.DockerOps.service.AesGcmEncryptionService;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;

@Converter
@RequiredArgsConstructor
public class SecretValueConverter implements AttributeConverter<String, String> {

    private final AesGcmEncryptionService encryptionService;

    @Override
    public String convertToDatabaseColumn(String plainText) {
        if (plainText == null) return null;
        return encryptionService.encrypt(plainText);
    }

    @Override
    public String convertToEntityAttribute(String cipherText) {
        if (cipherText == null) return null;
        return encryptionService.decrypt(cipherText);
    }
}
