package com.votewithblock.gateway.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;

@Entity
@Table(name = "biometric_attempt_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BiometricAttemptLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nationalIdHash;
    private String attemptType;
    private String outcome;
    private Double similarityScore;
    private Double thresholdUsed;
    private String ipHash;
    private OffsetDateTime attemptedAt;
    private String modelVersion;
}
