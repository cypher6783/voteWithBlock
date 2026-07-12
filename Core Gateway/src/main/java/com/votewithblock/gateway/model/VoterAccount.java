package com.votewithblock.gateway.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "voter_accounts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VoterAccount {
    @Id
    private String nationalId;
    private String fullName;
    private boolean hasVoted;
    private String faceEmbeddingReference;
    private String passwordSalt;
    private String passwordHash;
    private String biometricStatus = "NOT_ENROLLED";
    private int failedBiometricAttempts = 0;
    private java.time.OffsetDateTime lastFailedBiometricAt;
}
