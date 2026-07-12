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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import com.votewithblock.gateway.util.HashUtil;

@Service
@RequiredArgsConstructor
public class BiometricVerificationService {
    private static final Logger log = LoggerFactory.getLogger(BiometricVerificationService.class);

    private final VoterRepository voterRepository;
    private final VoterBiometricRepository voterBiometricRepository;
    private final BiometricAttemptLogRepository attemptLogRepository;
    private final FaceVerificationService aiClient;
    private final EmbeddingEncryptionService encryptionService;

    @Value("${app.biometric.similarity-threshold:0.82}")
    private float similarityThreshold;

    @Transactional
    public VerificationResult verifyIdentity(String nationalId, String base64Image, String clientIp) {
        VoterAccount voter = voterRepository.findById(nationalId).orElse(null);
        if (voter == null) return VerificationResult.FAIL_ERROR;

        // 1. Lockout Check
        if (voter.getFailedBiometricAttempts() >= 5 && voter.getLastFailedBiometricAt() != null) {
            if (voter.getLastFailedBiometricAt().plusMinutes(15).isAfter(OffsetDateTime.now())) {
                logAttempt(nationalId, "FAIL_LOCKED", null, clientIp, null);
                return VerificationResult.FAIL_LOCKED;
            } else {
                // Lockout expired, reset
                voter.setFailedBiometricAttempts(0);
                voter.setLastFailedBiometricAt(null);
                voterRepository.save(voter);
            }
        }

        // 2. Status Check
        if ("NOT_ENROLLED".equals(voter.getBiometricStatus())) return VerificationResult.FAIL_NOT_ENROLLED;
        if ("DISABLED".equals(voter.getBiometricStatus())) return VerificationResult.FAIL_DISABLED;

        // 3. Call AI Service first (needed for both enrollment and verification)
        FaceVerificationService.BiometricAiResponse aiResponse = aiClient.requestVerification(base64Image);
        if (aiResponse == null || !aiResponse.isLivenessPassed() || aiResponse.getEmbedding() == null) {
            handleFailure(voter);
            logAttempt(nationalId, "FAIL_LIVENESS", null, clientIp, aiResponse != null ? aiResponse.getModel_version() : null);
            return VerificationResult.FAIL_LIVENESS;
        }

        // 4. Load Embedding — if none exists, auto-enroll this first scan as the reference
        VoterBiometric biometric = voterBiometricRepository.findByNationalId(nationalId).orElse(null);
        if (biometric == null) {
            log.info("No stored embedding for voter {} — auto-enrolling first face scan as reference", nationalId);
            try {
                float[] freshEmbedding = toFloatArray(aiResponse.getEmbedding());
                EmbeddingEncryptionService.EncryptedEmbedding encrypted = encryptionService.encrypt(freshEmbedding);
                VoterBiometric newBiometric = new VoterBiometric();
                newBiometric.setNationalId(nationalId);
                newBiometric.setEmbeddingCiphertext(encrypted.ciphertext);
                newBiometric.setEmbeddingIv(encrypted.iv);
                newBiometric.setEmbeddingTag(encrypted.tag);
                newBiometric.setModelVersion(aiResponse.getModel_version());
                newBiometric.setEnrolledAt(java.time.OffsetDateTime.now());
                newBiometric.setEnrollmentIpHash(HashUtil.sha256Hex(clientIp));
                voterBiometricRepository.save(newBiometric);
                voter.setBiometricStatus("ACTIVE");
                voter.setFailedBiometricAttempts(0);
                voter.setLastFailedBiometricAt(null);
                voterRepository.save(voter);
                logAttempt(nationalId, "PASS", 1.0f, clientIp, aiResponse.getModel_version());
                return VerificationResult.PASS;
            } catch (Exception e) {
                log.error("Auto-enrollment error for voter {}", nationalId, e);
                return VerificationResult.FAIL_ERROR;
            }
        }

        // 5. Decrypt and Compare
        try {
            float[] storedEmbedding = encryptionService.decrypt(biometric.getEmbeddingCiphertext(), biometric.getEmbeddingIv(), biometric.getEmbeddingTag());
            float[] freshEmbedding = toFloatArray(aiResponse.getEmbedding());

            float similarity = cosineSimilarity(storedEmbedding, freshEmbedding);

            if (similarity >= similarityThreshold) {
                // Success
                voter.setFailedBiometricAttempts(0);
                voter.setLastFailedBiometricAt(null);
                voterRepository.save(voter);

                biometric.setLastVerifiedAt(OffsetDateTime.now());
                voterBiometricRepository.save(biometric);

                logAttempt(nationalId, "PASS", similarity, clientIp, aiResponse.getModel_version());
                return VerificationResult.PASS;

            } else if (similarity >= 0.75f) {
                // Soft trigger
                voter.setBiometricStatus("PENDING_REENROLLMENT");
                handleFailure(voter);
                logAttempt(nationalId, "FAIL_SIMILARITY", similarity, clientIp, aiResponse.getModel_version());
                return VerificationResult.FAIL_PENDING_REENROLLMENT;
            } else {
                // Hard failure
                handleFailure(voter);
                logAttempt(nationalId, "FAIL_SIMILARITY", similarity, clientIp, aiResponse.getModel_version());
                return VerificationResult.FAIL_SIMILARITY;
            }

        } catch (Exception e) {
            log.error("Decryption or comparison error", e);
            return VerificationResult.FAIL_ERROR;
        }
    }

    private void handleFailure(VoterAccount voter) {
        voter.setFailedBiometricAttempts(voter.getFailedBiometricAttempts() + 1);
        voter.setLastFailedBiometricAt(OffsetDateTime.now());
        voterRepository.save(voter);
    }

    private void logAttempt(String nationalId, String outcome, Float score, String ip, String modelVersion) {
        BiometricAttemptLog logEntry = BiometricAttemptLog.builder()
                .nationalIdHash(HashUtil.sha256Hex(nationalId))
                .attemptType("VERIFICATION")
                .outcome(outcome)
                .similarityScore(score != null ? (double) score : null)
                .thresholdUsed((double) similarityThreshold)
                .ipHash(HashUtil.sha256Hex(ip))
                .attemptedAt(OffsetDateTime.now())
                .modelVersion(modelVersion)
                .build();
        attemptLogRepository.save(logEntry);
    }

    private float cosineSimilarity(float[] a, float[] b) {
        float dotProduct = 0;
        float normA = 0;
        float normB = 0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return (float) (dotProduct / (Math.sqrt(normA) * Math.sqrt(normB)));
    }

    private float[] toFloatArray(List<Float> list) {
        float[] arr = new float[list.size()];
        for(int i = 0; i < list.size(); i++) arr[i] = list.get(i);
        return arr;
    }

    public enum VerificationResult {
        PASS,
        FAIL_LIVENESS,
        FAIL_SIMILARITY,
        FAIL_LOCKED,
        FAIL_NOT_ENROLLED,
        FAIL_PENDING_REENROLLMENT,
        FAIL_DISABLED,
        FAIL_ERROR
    }
}
