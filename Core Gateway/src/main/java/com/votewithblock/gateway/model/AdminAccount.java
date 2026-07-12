package com.votewithblock.gateway.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "admin_accounts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminAccount {
    @Id
    private String username;
    
    private String fullName;
    private String email;
    
    @Enumerated(EnumType.STRING)
    private AdminRole role;
    
    private String passwordSalt;
    private String passwordHash;
    
    private String mfaSecret;
    private boolean mfaEnabled;
    private boolean isActive;
    
    private int failedLoginAttempts;
    private OffsetDateTime lastFailedLoginAt;
    
    private OffsetDateTime createdAt;
    private OffsetDateTime lastLoginAt;
    private String createdBy;
}
