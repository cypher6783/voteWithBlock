package com.votewithblock.gateway.service;

import com.votewithblock.gateway.dto.MonitoringDashboardDto;
import com.votewithblock.gateway.model.HealthHistory;
import com.votewithblock.gateway.repository.HealthHistoryRepository;
import com.votewithblock.gateway.util.HashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
@RequiredArgsConstructor
public class SystemHealthService {

    private final VotingLedgerService votingLedgerService;
    private final JdbcTemplate jdbcTemplate;
    private final HealthHistoryRepository healthHistoryRepository;

    @Value("${services.python-ai.url:http://localhost:8000}")
    private String aiServiceUrl;

    private final AtomicInteger gatewayFailures = new AtomicInteger(0);
    private final AtomicInteger gatewaySuccesses = new AtomicInteger(0);
    private final AtomicInteger aiFailures = new AtomicInteger(0);
    private final AtomicInteger aiSuccesses = new AtomicInteger(0);
    private final AtomicInteger dbFailures = new AtomicInteger(0);
    private final AtomicInteger dbSuccesses = new AtomicInteger(0);
    private final AtomicInteger fabricFailures = new AtomicInteger(0);
    private final AtomicInteger fabricSuccesses = new AtomicInteger(0);

    private volatile String gatewayStatus = "HEALTHY";
    private volatile String aiStatus = "HEALTHY";
    private volatile String dbStatus = "HEALTHY";
    private volatile String fabricStatus = "HEALTHY";

    @Scheduled(fixedRate = 10000)
    public void performHealthChecks() {
        checkGateway();
        checkDatabase();
        checkAiService();
        checkFabric();
        persistHealthHistory();
    }

    private void checkGateway() {
        try {
            gatewaySuccesses.incrementAndGet();
            gatewayFailures.set(0);
            if (gatewaySuccesses.get() >= 3) {
                gatewayStatus = "HEALTHY";
            }
        } catch (Exception e) {
            gatewaySuccesses.set(0);
            int failures = gatewayFailures.incrementAndGet();
            if (failures >= 5) {
                gatewayStatus = "CRITICAL";
            } else if (failures >= 3) {
                gatewayStatus = "DEGRADED";
            }
        }
    }

    private void checkDatabase() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            dbSuccesses.incrementAndGet();
            dbFailures.set(0);
            if (dbSuccesses.get() >= 3) {
                dbStatus = "HEALTHY";
            }
        } catch (Exception e) {
            dbSuccesses.set(0);
            int failures = dbFailures.incrementAndGet();
            if (failures >= 5) {
                dbStatus = "CRITICAL";
            } else if (failures >= 3) {
                dbStatus = "DEGRADED";
            }
        }
    }

    private void checkAiService() {
        try {
            RestTemplate restTemplate = new RestTemplate();
            var response = restTemplate.getForObject(aiServiceUrl + "/health", String.class);
            aiSuccesses.incrementAndGet();
            aiFailures.set(0);
            if (aiSuccesses.get() >= 3) {
                aiStatus = "HEALTHY";
            }
        } catch (Exception e) {
            aiSuccesses.set(0);
            int failures = aiFailures.incrementAndGet();
            if (failures >= 5) {
                aiStatus = "CRITICAL";
            } else if (failures >= 3) {
                aiStatus = "DEGRADED";
            }
        }
    }

    private void checkFabric() {
        try {
            if (votingLedgerService.isContractAvailable()) {
                fabricSuccesses.incrementAndGet();
                fabricFailures.set(0);
                if (fabricSuccesses.get() >= 3) {
                    fabricStatus = "HEALTHY";
                }
            } else {
                fabricSuccesses.set(0);
                int failures = fabricFailures.incrementAndGet();
                if (failures >= 5) {
                    fabricStatus = "CRITICAL";
                } else if (failures >= 3) {
                    fabricStatus = "DEGRADED";
                }
            }
        } catch (Exception e) {
            fabricSuccesses.set(0);
            int failures = fabricFailures.incrementAndGet();
            if (failures >= 5) {
                fabricStatus = "CRITICAL";
            } else if (failures >= 3) {
                fabricStatus = "DEGRADED";
            }
        }
    }

    private void persistHealthHistory() {
        try {
            String metrics = buildResourceMetricsJson();
            String overallStatus = calculateOverallStatus();

            String[] components = {"gateway", "ai-service", "database", "fabric"};
            String[] statuses = {gatewayStatus, aiStatus, dbStatus, fabricStatus};

            for (int i = 0; i < components.length; i++) {
                HealthHistory history = HealthHistory.builder()
                        .component(components[i])
                        .status(statuses[i])
                        .metrics(metrics)
                        .recordedAt(OffsetDateTime.now())
                        .build();
                healthHistoryRepository.save(history);
            }
        } catch (Exception e) {
            log.error("Failed to persist health history", e);
        }
    }

    private String buildResourceMetricsJson() {
        Runtime runtime = Runtime.getRuntime();
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();

        long freeMemory = runtime.freeMemory();
        long totalMemory = runtime.totalMemory();
        long maxMemory = runtime.maxMemory();
        double cpuLoad = osBean.getSystemLoadAverage();
        int availableProcessors = osBean.getAvailableProcessors();
        int threadCount = threadBean.getThreadCount();
        int peakThreadCount = threadBean.getPeakThreadCount();

        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        long heapUsed = heapUsage.getUsed();
        long heapMax = heapUsage.getMax();

        return String.format(
            "{\"cpuLoad\":%.2f,\"availableProcessors\":%d,\"freeMemory\":%d,\"totalMemory\":%d,\"maxMemory\":%d,\"heapUsed\":%d,\"heapMax\":%d,\"threadCount\":%d,\"peakThreadCount\":%d}",
            cpuLoad, availableProcessors, freeMemory, totalMemory, maxMemory, heapUsed, heapMax, threadCount, peakThreadCount
        );
    }

    public MonitoringDashboardDto getSystemHealth() {
        String overallStatus = calculateOverallStatus();

        MonitoringDashboardDto.ComponentHealth gateway = MonitoringDashboardDto.ComponentHealth.builder()
                .status(gatewayStatus).details("Gateway service").build();
        MonitoringDashboardDto.ComponentHealth ai = MonitoringDashboardDto.ComponentHealth.builder()
                .status(aiStatus).details("AI verification service").build();
        MonitoringDashboardDto.ComponentHealth db = MonitoringDashboardDto.ComponentHealth.builder()
                .status(dbStatus).details("PostgreSQL database").build();
        MonitoringDashboardDto.ComponentHealth fabric = MonitoringDashboardDto.ComponentHealth.builder()
                .status(fabricStatus).details("Hyperledger Fabric network").build();

        MonitoringDashboardDto.BlockchainMetrics blockchain = MonitoringDashboardDto.BlockchainMetrics.builder()
                .lastSuccessfulTransaction(votingLedgerService.getLastSuccessfulTransaction())
                .lastFailedTransaction(votingLedgerService.getLastFailedTransaction())
                .averageSubmissionLatencyMs(votingLedgerService.getAverageSubmissionLatencyMs())
                .averageQueryLatencyMs(votingLedgerService.getAverageQueryLatencyMs())
                .build();

        return MonitoringDashboardDto.builder()
                .overallStatus(overallStatus)
                .gatewayHealth(gateway)
                .aiServiceHealth(ai)
                .databaseHealth(db)
                .fabricHealth(fabric)
                .blockchainMetrics(blockchain)
                .build();
    }

    private String calculateOverallStatus() {
        int criticalCount = 0;
        int degradedCount = 0;

        if ("CRITICAL".equals(gatewayStatus)) criticalCount++;
        else if ("DEGRADED".equals(gatewayStatus)) degradedCount++;

        if ("CRITICAL".equals(aiStatus)) criticalCount++;
        else if ("DEGRADED".equals(aiStatus)) degradedCount++;

        if ("CRITICAL".equals(dbStatus)) criticalCount++;
        else if ("DEGRADED".equals(dbStatus)) degradedCount++;

        if ("CRITICAL".equals(fabricStatus)) criticalCount++;
        else if ("DEGRADED".equals(fabricStatus)) degradedCount++;

        if (criticalCount >= 2) return "CRITICAL";
        if (criticalCount >= 1 || degradedCount >= 2) return "DEGRADED";
        return "HEALTHY";
    }
}
