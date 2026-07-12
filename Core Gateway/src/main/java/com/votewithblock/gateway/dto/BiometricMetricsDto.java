package com.votewithblock.gateway.dto;

import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class BiometricMetricsDto {
    private long successfulVerifications;
    private long failedVerifications;
    private long lockouts;
    private long reEnrollmentRequests;
}
