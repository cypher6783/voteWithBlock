package com.votewithblock.gateway.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class EmbeddingEncryptionService {
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BIT = 128;
    private static final int GCM_IV_LENGTH_BYTE = 12;

    private final SecretKeySpec secretKey;
    private final SecureRandom secureRandom;

    public EmbeddingEncryptionService(@Value("${app.biometric.encryption-key}") String encryptionKeyBase64) {
        if (encryptionKeyBase64 == null || encryptionKeyBase64.isBlank()) {
            throw new IllegalStateException(
                "app.biometric.encryption-key is required. Generate one with: python -c \"import secrets; print(secrets.token_urlsafe(32))\"");
        }
        byte[] keyBytes = Base64.getDecoder().decode(encryptionKeyBase64);
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException("Encryption key must be exactly 32 bytes for AES-256");
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
        this.secureRandom = new SecureRandom();
    }

    public EncryptedEmbedding encrypt(float[] embedding) throws Exception {
        byte[] iv = new byte[GCM_IV_LENGTH_BYTE];
        secureRandom.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BIT, iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

        byte[] plaintext = floatArrayToByteArray(embedding);
        byte[] cipherTextWithTag = cipher.doFinal(plaintext);

        // GCM places the tag at the end of the ciphertext
        int tagLength = GCM_TAG_LENGTH_BIT / 8;
        int cipherTextLength = cipherTextWithTag.length - tagLength;

        byte[] cipherText = new byte[cipherTextLength];
        byte[] tag = new byte[tagLength];

        System.arraycopy(cipherTextWithTag, 0, cipherText, 0, cipherTextLength);
        System.arraycopy(cipherTextWithTag, cipherTextLength, tag, 0, tagLength);

        return new EncryptedEmbedding(cipherText, iv, tag);
    }

    public float[] decrypt(byte[] ciphertext, byte[] iv, byte[] tag) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BIT, iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

        byte[] cipherTextWithTag = new byte[ciphertext.length + tag.length];
        System.arraycopy(ciphertext, 0, cipherTextWithTag, 0, ciphertext.length);
        System.arraycopy(tag, 0, cipherTextWithTag, ciphertext.length, tag.length);

        byte[] plaintext = cipher.doFinal(cipherTextWithTag);
        return byteArrayToFloatArray(plaintext);
    }

    private byte[] floatArrayToByteArray(float[] floats) {
        ByteBuffer buffer = ByteBuffer.allocate(floats.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        buffer.asFloatBuffer().put(floats);
        return buffer.array();
    }

    private float[] byteArrayToFloatArray(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] floats = new float[bytes.length / 4];
        buffer.asFloatBuffer().get(floats);
        return floats;
    }

    public static class EncryptedEmbedding {
        public final byte[] ciphertext;
        public final byte[] iv;
        public final byte[] tag;

        public EncryptedEmbedding(byte[] ciphertext, byte[] iv, byte[] tag) {
            this.ciphertext = ciphertext;
            this.iv = iv;
            this.tag = tag;
        }
    }
}
