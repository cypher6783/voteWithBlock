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
@Table(name = "admin_action_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminActionLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String adminUsernameHash;
    private String action;
    private String targetEntityType;
    private String targetEntityIdHash;
    private String outcome;
    private String ipHash;
    private OffsetDateTime performedAt;
    private String metadata;
}
