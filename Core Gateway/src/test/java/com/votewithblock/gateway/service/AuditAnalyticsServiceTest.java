package com.votewithblock.gateway.service;

import com.votewithblock.gateway.model.AdminActionLog;
import com.votewithblock.gateway.model.AuditExport;
import com.votewithblock.gateway.repository.AdminActionLogRepository;
import com.votewithblock.gateway.repository.AuditExportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditAnalyticsServiceTest {

    @Mock
    private AdminActionLogRepository adminActionLogRepository;

    @Mock
    private AuditExportRepository auditExportRepository;

    @InjectMocks
    private AuditAnalyticsService auditService;

    private AdminActionLog testLog;

    @BeforeEach
    void setUp() {
        testLog = AdminActionLog.builder()
                .id(1L)
                .adminUsernameHash("abc123")
                .action("CREATE_ELECTION")
                .targetEntityType("ELECTION")
                .targetEntityIdHash("def456")
                .outcome("SUCCESS")
                .ipHash("ghi789")
                .performedAt(OffsetDateTime.now())
                .metadata("{}")
                .build();
    }

    @Test
    void getAuditLogs_shouldReturnPageWithActionFilter() {
        Page<AdminActionLog> page = new PageImpl<>(List.of(testLog));
        when(adminActionLogRepository.findByActionContaining("ELECTION", PageRequest.of(0, 20)))
                .thenReturn(page);

        Page<AdminActionLog> result = auditService.getAuditLogs(
                "ELECTION", null, null, null, null, null, 0, 20);

        assertEquals(1, result.getContent().size());
        assertEquals("CREATE_ELECTION", result.getContent().get(0).getAction());
    }

    @Test
    void getAuditLogs_shouldReturnPageWithOutcomeFilter() {
        Page<AdminActionLog> page = new PageImpl<>(List.of(testLog));
        when(adminActionLogRepository.findByOutcome("SUCCESS", PageRequest.of(0, 20)))
                .thenReturn(page);

        Page<AdminActionLog> result = auditService.getAuditLogs(
                null, null, null, "SUCCESS", null, null, 0, 20);

        assertEquals(1, result.getContent().size());
        assertEquals("SUCCESS", result.getContent().get(0).getOutcome());
    }

    @Test
    void getAuditLog_shouldReturnLog() {
        when(adminActionLogRepository.findById(1L)).thenReturn(Optional.of(testLog));

        AdminActionLog result = auditService.getAuditLog(1L);

        assertNotNull(result);
        assertEquals("CREATE_ELECTION", result.getAction());
    }

    @Test
    void getAuditLog_shouldThrowWhenNotFound() {
        when(adminActionLogRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> auditService.getAuditLog(999L));
    }

    @Test
    void getAuditStats_shouldReturnCounts() {
        when(adminActionLogRepository.count()).thenReturn(100L);
        when(adminActionLogRepository.countByPerformedAtAfter(any(OffsetDateTime.class))).thenReturn(50L);
        when(adminActionLogRepository.countByOutcome("SUCCESS")).thenReturn(80L);
        when(adminActionLogRepository.countByOutcome("FAIL")).thenReturn(20L);

        var stats = auditService.getAuditStats();

        assertEquals(100L, stats.get("total"));
        assertEquals(80L, stats.get("successCount"));
        assertEquals(20L, stats.get("failCount"));
    }

    @Test
    void exportAuditLogs_shouldReturnExportWithHash() {
        Page<AdminActionLog> page = new PageImpl<>(List.of(testLog));
        when(adminActionLogRepository.findByPerformedAtBetween(
                any(OffsetDateTime.class), any(OffsetDateTime.class), any(PageRequest.class)))
                .thenReturn(page);
        when(auditExportRepository.save(any(AuditExport.class))).thenReturn(AuditExport.builder()
                .id(1L)
                .generatedAt(OffsetDateTime.now())
                .generatedBy("admin1")
                .recordCount(1)
                .sha256Hash("abc123def456")
                .build());

        AuditExport result = auditService.exportAuditLogs("admin1",
                OffsetDateTime.now().minusDays(1), OffsetDateTime.now());

        assertNotNull(result);
        assertEquals(1, result.getRecordCount());
        assertNotNull(result.getSha256Hash());
        verify(auditExportRepository).save(any(AuditExport.class));
    }
}
