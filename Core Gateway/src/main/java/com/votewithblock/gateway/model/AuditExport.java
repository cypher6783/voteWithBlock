package com.votewithblock.gateway.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "audit_exports")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditExport {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private OffsetDateTime generatedAt;
    private String generatedBy;
    private Integer recordCount;
    private String sha256Hash;
    private String filePath;
}
