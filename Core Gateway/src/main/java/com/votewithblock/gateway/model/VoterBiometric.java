package com.votewithblock.gateway.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;

@Entity
@Table(name = "voter_biometrics")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoterBiometric {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String nationalId;

    @Column(nullable = false)
    private byte[] embeddingCiphertext;

    @Column(nullable = false)
    private byte[] embeddingIv;

    @Column(nullable = false)
    private byte[] embeddingTag;

    @Column(nullable = false)
    private String modelVersion;

    @Column(nullable = false)
    private OffsetDateTime enrolledAt;

    private String enrollmentIpHash;

    private OffsetDateTime lastVerifiedAt;

    @Version
    private int version;
}
