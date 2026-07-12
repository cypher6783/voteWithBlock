package com.votewithblock.gateway.service;

import com.votewithblock.gateway.model.SecurityEvent;
import com.votewithblock.gateway.model.SecurityEventType;
import com.votewithblock.gateway.repository.SecurityEventRepository;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecurityEventServiceTest {

    @Mock
    private SecurityEventRepository securityEventRepository;

    @InjectMocks
    private SecurityEventService securityEventService;

    private SecurityEvent testEvent;

    @BeforeEach
    void setUp() {
        testEvent = SecurityEvent.builder()
                .id(1L)
                .eventType(SecurityEventType.FAILED_LOGIN)
                .severity("HIGH")
                .source("AUTH")
                .ipHash("abc123")
                .userHash("user456")
                .details("Invalid credentials")
                .occurredAt(OffsetDateTime.now())
                .build();
    }

    @Test
    void recordEvent_shouldSaveAndReturnEvent() {
        when(securityEventRepository.save(any(SecurityEvent.class))).thenReturn(testEvent);

        SecurityEvent result = securityEventService.recordEvent(
                SecurityEventType.FAILED_LOGIN, "HIGH", "AUTH", "abc123", "user456", "Invalid credentials");

        assertNotNull(result);
        assertEquals(SecurityEventType.FAILED_LOGIN, result.getEventType());
        assertEquals("HIGH", result.getSeverity());
        assertEquals("AUTH", result.getSource());
        verify(securityEventRepository, times(1)).save(any(SecurityEvent.class));
    }

    @Test
    void getEvents_shouldReturnPage() {
        Page<SecurityEvent> page = new PageImpl<>(List.of(testEvent));
        when(securityEventRepository.findByEventType(SecurityEventType.FAILED_LOGIN, PageRequest.of(0, 10)))
                .thenReturn(page);

        Page<SecurityEvent> result = securityEventService.getEvents(SecurityEventType.FAILED_LOGIN, 0, 10);

        assertEquals(1, result.getContent().size());
        assertEquals(SecurityEventType.FAILED_LOGIN, result.getContent().get(0).getEventType());
    }

    @Test
    void getEventsInRange_shouldReturnPage() {
        OffsetDateTime start = OffsetDateTime.now().minusDays(7);
        OffsetDateTime end = OffsetDateTime.now();
        Page<SecurityEvent> page = new PageImpl<>(List.of(testEvent));
        when(securityEventRepository.findByOccurredAtBetween(start, end, PageRequest.of(0, 10)))
                .thenReturn(page);

        Page<SecurityEvent> result = securityEventService.getEventsInRange(start, end, 0, 10);

        assertEquals(1, result.getContent().size());
    }

    @Test
    void getEventStats_shouldReturnCounts() {
        when(securityEventRepository.countByOccurredAtAfter(any(OffsetDateTime.class))).thenReturn(50L);
        when(securityEventRepository.countByEventType(SecurityEventType.FAILED_LOGIN)).thenReturn(20L);
        when(securityEventRepository.countByEventType(SecurityEventType.FAILED_ADMIN_LOGIN)).thenReturn(5L);
        when(securityEventRepository.countByEventType(SecurityEventType.RATE_LIMIT)).thenReturn(15L);
        when(securityEventRepository.countByEventType(SecurityEventType.ACCOUNT_LOCKOUT)).thenReturn(3L);
        when(securityEventRepository.countByEventType(SecurityEventType.BIOMETRIC_FAILURE)).thenReturn(8L);
        when(securityEventRepository.countByEventType(SecurityEventType.SUSPICIOUS_ACTIVITY)).thenReturn(2L);

        var stats = securityEventService.getEventStats();

        assertEquals(50L, stats.get("total24h"));
        assertEquals(20L, stats.get("failedLogin"));
        assertEquals(5L, stats.get("failedAdminLogin"));
        assertEquals(15L, stats.get("rateLimit"));
        assertEquals(3L, stats.get("accountLockout"));
    }
}
