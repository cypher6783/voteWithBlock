package com.votewithblock.gateway.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "admin_sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminSession {
    @Id
    private String jti;
    
    private String username;
    private String sessionType;
    
    private OffsetDateTime issuedAt;
    private OffsetDateTime expiresAt;
    private boolean revoked;
}
