package com.votewithblock.gateway.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "recovery_audit_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecoveryAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String actionType;

    @Column(nullable = false)
    private String performedBy;

    @Column(nullable = false)
    private OffsetDateTime performedAt;

    @Column(nullable = false)
    private String targetComponent;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String details;

    @Column(nullable = false)
    private String outcome;

    private Long durationMs;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    @Column(nullable = false)
    private String sha256Hash;
}
