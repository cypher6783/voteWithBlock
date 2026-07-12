package com.votewithblock.gateway.service;

import com.votewithblock.gateway.model.BiometricAttemptLog;
import com.votewithblock.gateway.model.VoterAccount;
import com.votewithblock.gateway.model.VoterBiometric;
import com.votewithblock.gateway.repository.BiometricAttemptLogRepository;
import com.votewithblock.gateway.repository.VoterRepository;
import com.votewithblock.gateway.repository.VoterBiometricRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import com.votewithblock.gateway.util.HashUtil;

@Service
@RequiredArgsConstructor
public class BiometricEnrollmentService {

    private static final Logger log = LoggerFactory.getLogger(BiometricEnrollmentService.class);

    private final VoterRepository voterRepository;
    private final VoterBiometricRepository voterBiometricRepository;
    private final BiometricAttemptLogRepository attemptLogRepository;
    private final FaceVerificationService aiClient;
    private final EmbeddingEncryptionService encryptionService;

    @Transactional
    public EnrollmentResult enrollVoter(String nationalId, String base64Image, String clientIp) {
        VoterAccount voter = voterRepository.findById(nationalId).orElse(null);
        if (voter == null) return EnrollmentResult.FAIL_NOT_FOUND;

        FaceVerificationService.BiometricAiResponse aiResponse = aiClient.requestEnrollment(base64Image);
        
        if (aiResponse == null || !aiResponse.isLivenessPassed() || aiResponse.getEmbedding() == null) {
            logAttempt(nationalId, "FAIL_QUALITY", clientIp, aiResponse != null ? aiResponse.getModel_version() : null);
            return EnrollmentResult.FAIL_QUALITY;
        }

        try {
            float[] freshEmbedding = toFloatArray(aiResponse.getEmbedding());
            EmbeddingEncryptionService.EncryptedEmbedding encrypted = encryptionService.encrypt(freshEmbedding);

            VoterBiometric biometric = voterBiometricRepository.findByNationalId(nationalId).orElse(new VoterBiometric());
            biometric.setNationalId(nationalId);
            biometric.setEmbeddingCiphertext(encrypted.ciphertext);
            biometric.setEmbeddingIv(encrypted.iv);
            biometric.setEmbeddingTag(encrypted.tag);
            biometric.setModelVersion(aiResponse.getModel_version());
            biometric.setEnrolledAt(OffsetDateTime.now());
            biometric.setEnrollmentIpHash(HashUtil.sha256Hex(clientIp));

            voterBiometricRepository.save(biometric);

            voter.setBiometricStatus("ACTIVE");
            voter.setFailedBiometricAttempts(0);
            voter.setLastFailedBiometricAt(null);
            voterRepository.save(voter);

            logAttempt(nationalId, "PASS", clientIp, aiResponse.getModel_version());
            return EnrollmentResult.SUCCESS;

        } catch (Exception e) {
            log.error("Encryption error during enrollment", e);
            return EnrollmentResult.FAIL_ERROR;
        }
    }

    private void logAttempt(String nationalId, String outcome, String ip, String modelVersion) {
        BiometricAttemptLog logEntry = BiometricAttemptLog.builder()
                .nationalIdHash(HashUtil.sha256Hex(nationalId))
                .attemptType("ENROLLMENT")
                .outcome(outcome)
                .ipHash(HashUtil.sha256Hex(ip))
                .attemptedAt(OffsetDateTime.now())
                .modelVersion(modelVersion)
                .build();
        attemptLogRepository.save(logEntry);
    }

    private float[] toFloatArray(java.util.List<Float> list) {
        float[] arr = new float[list.size()];
        for(int i = 0; i < list.size(); i++) arr[i] = list.get(i);
        return arr;
    }

    public enum EnrollmentResult {
        SUCCESS,
        FAIL_NOT_FOUND,
        FAIL_QUALITY,
        FAIL_ERROR
    }
}
