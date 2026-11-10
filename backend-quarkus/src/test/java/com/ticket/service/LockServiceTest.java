package com.ticket.service;

import com.ticket.domain.CheckInEvent;
import com.ticket.domain.Ticket;
import com.ticket.domain.TicketStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

public class LockServiceTest {

    private LockService lockService;

    @BeforeEach
    void setUp() {
        // Uses default fallback constructor without redis datasource
        lockService = new LockService();
    }

    @Test
    void testLockAcquisitionFallback() {
        Boolean acquired = lockService.acquireLock("VIP-101", "USER-1", Duration.ofMinutes(10))
            .await().indefinitely();
        assertTrue(acquired, "Lock should be granted in test/fallback mode");
    }

    @Test
    void testLockReleaseFallback() {
        Boolean released = lockService.releaseLock("VIP-101", "USER-1")
            .await().indefinitely();
        assertTrue(released, "Lock should be released in fallback mode");
    }

    @Test
    void testLockKeyGeneration() {
        assertEquals("ticket:TICK-1:lock", lockService.buildLockKey("TICK-1"));
    }
}
