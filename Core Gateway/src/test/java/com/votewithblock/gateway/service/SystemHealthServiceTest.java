package com.votewithblock.gateway.service;

import com.votewithblock.gateway.dto.MonitoringDashboardDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SystemHealthServiceTest {

    private StubVotingLedgerService stubLedger;
    private SystemHealthService healthService;

    static class StubVotingLedgerService extends VotingLedgerService {
        boolean contractAvailable = false;
        String lastSuccess = "none";
        String lastFailed = "none";
        double avgSubmit = 0;
        double avgQuery = 0;

        StubVotingLedgerService() {
        }

        @Override
        public boolean isContractAvailable() { return contractAvailable; }
        @Override
        public String getLastSuccessfulTransaction() { return lastSuccess; }
        @Override
        public String getLastFailedTransaction() { return lastFailed; }
        @Override
        public double getAverageSubmissionLatencyMs() { return avgSubmit; }
        @Override
        public double getAverageQueryLatencyMs() { return avgQuery; }
    }

    @BeforeEach
    void setUp() {
        stubLedger = new StubVotingLedgerService();
        healthService = new SystemHealthService(stubLedger, null, null);
    }

    @Test
    void getSystemHealth_shouldReturnDtoWithAllComponents() {
        MonitoringDashboardDto result = healthService.getSystemHealth();

        assertNotNull(result);
        assertNotNull(result.getOverallStatus());
        assertNotNull(result.getGatewayHealth());
        assertNotNull(result.getAiServiceHealth());
        assertNotNull(result.getDatabaseHealth());
        assertNotNull(result.getFabricHealth());
        assertNotNull(result.getBlockchainMetrics());
    }

    @Test
    void getSystemHealth_shouldReturnValidStatusStrings() {
        MonitoringDashboardDto result = healthService.getSystemHealth();

        String status = result.getOverallStatus();
        assertTrue(
                status.equals("HEALTHY") || status.equals("DEGRADED") || status.equals("CRITICAL"),
                "Status must be HEALTHY, DEGRADED, or CRITICAL but was: " + status
        );
    }

    @Test
    void getSystemHealth_shouldReturnComponentHealthWithStatusAndDetails() {
        MonitoringDashboardDto result = healthService.getSystemHealth();

        assertNotNull(result.getGatewayHealth().getStatus());
        assertNotNull(result.getGatewayHealth().getDetails());
        assertNotNull(result.getAiServiceHealth().getStatus());
        assertNotNull(result.getAiServiceHealth().getDetails());
        assertNotNull(result.getDatabaseHealth().getStatus());
        assertNotNull(result.getDatabaseHealth().getDetails());
        assertNotNull(result.getFabricHealth().getStatus());
        assertNotNull(result.getFabricHealth().getDetails());
    }

    @Test
    void getSystemHealth_shouldReturnBlockchainMetricsNonNegative() {
        stubLedger.avgSubmit = 120.5;
        stubLedger.avgQuery = 45.2;

        MonitoringDashboardDto result = healthService.getSystemHealth();

        MonitoringDashboardDto.BlockchainMetrics metrics = result.getBlockchainMetrics();
        assertNotNull(metrics);
        assertEquals(120.5, metrics.getAverageSubmissionLatencyMs());
        assertEquals(45.2, metrics.getAverageQueryLatencyMs());
    }
}
