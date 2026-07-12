package com.votewithblock.gateway.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "backup_catalog")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BackupCatalog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String backupId;

    @Column(nullable = false)
    private String backupType;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private long sizeBytes;

    @Column(nullable = false)
    private String sha256Hash;

    @Column(nullable = false)
    private String storageLocation;

    private boolean verified = false;

    private OffsetDateTime verifiedAt;

    @Column(nullable = false)
    private int retentionDays;

    @Column(columnDefinition = "TEXT")
    private String metadata;
}
