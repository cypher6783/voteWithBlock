package com.votewithblock.gateway.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "vote_request_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoteRequestLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    @Column(nullable = false)
    private String voterIdHash;

    @Column(nullable = false)
    private UUID electionId;

    @Column(nullable = false)
    private UUID candidateId;

    @Column(nullable = false)
    private String status;

    private String transactionId;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    private OffsetDateTime processedAt;

    private int retryCount = 0;

    @Column(columnDefinition = "TEXT")
    private String lastError;
}
