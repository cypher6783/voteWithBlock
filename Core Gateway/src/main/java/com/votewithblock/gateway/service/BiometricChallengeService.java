package com.votewithblock.gateway.service;

import com.votewithblock.gateway.model.BiometricChallenge;
import com.votewithblock.gateway.repository.BiometricChallengeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BiometricChallengeService {

    private final BiometricChallengeRepository challengeRepository;

    @Value("${app.biometric.challenge-ttl-seconds:90}")
    private long challengeTtlSeconds;

    @Transactional
    public BiometricChallenge createChallenge(String nationalId) {
        BiometricChallenge challenge = BiometricChallenge.builder()
                .challengeId(UUID.randomUUID())
                .nationalId(nationalId)
                .expiresAt(OffsetDateTime.now().plusSeconds(challengeTtlSeconds))
                .used(false)
                .build();
        return challengeRepository.save(challenge);
    }

    @Transactional
    public boolean validateAndConsumeChallenge(UUID challengeId, String nationalId) {
        BiometricChallenge challenge = challengeRepository.findByChallengeIdAndNationalId(challengeId, nationalId)
                .orElse(null);

        if (challenge == null || challenge.isUsed() || challenge.getExpiresAt().isBefore(OffsetDateTime.now())) {
            return false;
        }

        challenge.setUsed(true);
        challengeRepository.save(challenge);
        return true;
    }
}
