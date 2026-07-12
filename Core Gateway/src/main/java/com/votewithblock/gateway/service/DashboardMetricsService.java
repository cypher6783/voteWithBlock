package com.votewithblock.gateway.service;

import com.votewithblock.gateway.dto.BiometricMetricsDto;
import com.votewithblock.gateway.dto.SecurityDashboardDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
@RequiredArgsConstructor
public class DashboardMetricsService {

    // These would normally be persisted in a database table or Redis
    // Using AtomicLong for thread-safe in-memory counters as a placeholder
    private final AtomicLong successfulVerifications = new AtomicLong(0);
    private final AtomicLong failedVerifications = new AtomicLong(0);
    private final AtomicLong lockouts = new AtomicLong(0);
    private final AtomicLong reEnrollmentRequests = new AtomicLong(0);

    private final AtomicLong loginFailures = new AtomicLong(0);
    private final AtomicLong adminLoginFailures = new AtomicLong(0);
    private final AtomicLong rateLimitEvents = new AtomicLong(0);
    private final AtomicLong suspiciousActivities = new AtomicLong(0);

    public void incrementSuccessfulVerification() {
        successfulVerifications.incrementAndGet();
    }

    public void incrementFailedVerification() {
        failedVerifications.incrementAndGet();
    }

    public void incrementLockout() {
        lockouts.incrementAndGet();
    }

    public void incrementReEnrollmentRequest() {
        reEnrollmentRequests.incrementAndGet();
    }

    public void incrementLoginFailure() {
        loginFailures.incrementAndGet();
    }

    public void incrementAdminLoginFailure() {
        adminLoginFailures.incrementAndGet();
    }

    public void incrementRateLimitEvent() {
        rateLimitEvents.incrementAndGet();
    }

    public void incrementSuspiciousActivity() {
        suspiciousActivities.incrementAndGet();
    }

    public BiometricMetricsDto getBiometricMetrics() {
        return BiometricMetricsDto.builder()
                .successfulVerifications(successfulVerifications.get())
                .failedVerifications(failedVerifications.get())
                .lockouts(lockouts.get())
                .reEnrollmentRequests(reEnrollmentRequests.get())
                .build();
    }

    public SecurityDashboardDto getSecurityMetrics() {
        return SecurityDashboardDto.builder()
                .loginFailures(loginFailures.get())
                .adminLoginFailures(adminLoginFailures.get())
                .rateLimitEvents(rateLimitEvents.get())
                .suspiciousActivities(suspiciousActivities.get())
                .build();
    }
}
