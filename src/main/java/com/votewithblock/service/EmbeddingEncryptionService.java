package com.votewithblock.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class EmbeddingEncryptionService {

    @Value("${app.encryption.key}")
    private String encryptionKeyBase64;

    private static final String AES_GCM = "AES/GCM/PKCS5Padding";
    private static final int KEY_SIZE = 256;
    private static final int GCM_TAG_LENGTH = 128;

    public String encrypt(byte[] embedding) throws Exception {
        SecretKey secretKey = getSecretKey();
        Cipher cipher = Cipher.getInstance(AES_GCM);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        byte[] iv = cipher.getIV();
        byte[] encrypted = cipher.doFinal(embedding);
        // Combine IV and encrypted bytes
        ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
        buffer.put(iv);
        buffer.put(encrypted);
        return Base64.getEncoder().encodeToString(buffer.array());
    }

    public byte[] decrypt(String encryptedData) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(Base64.getDecoder().decode(encryptedData));
        byte[] iv = new byte[cipher.getIV().getLength()];
        buffer.get(iv);
        byte[] encryptedBytes = new byte[buffer.remaining()];
        buffer.get(encryptedBytes);
        SecretKey secretKey = getSecretKey();
        Cipher cipher = Cipher.getInstance(AES_GCM);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        return cipher.doFinal(encryptedBytes);
    }

    private SecretKey getSecretKey() throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(encryptionKeyBase64);
        return new javax.crypto.spec.SecretKeySpec(keyBytes, "AES");
    }
}