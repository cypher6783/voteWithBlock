package com.votewithblock.gateway.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "biometric_challenges")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BiometricChallenge {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private UUID challengeId;

    @Column(nullable = false)
    private String nationalId;

    @Column(nullable = false)
    private OffsetDateTime expiresAt;

    @Builder.Default
    private boolean used = false;
}
