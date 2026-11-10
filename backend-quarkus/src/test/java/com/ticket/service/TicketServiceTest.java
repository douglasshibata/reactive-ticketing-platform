package com.ticket.service;

import com.ticket.domain.CheckInEvent;
import com.ticket.domain.Ticket;
import com.ticket.domain.TicketStatus;
import com.ticket.messaging.TicketEventProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TicketServiceTest {

    private TicketService ticketService;
    private LockService lockService;

    @BeforeEach
    void setUp() throws Exception {
        ticketService = new TicketService();
        lockService = new LockService();

        // Inject dependencies manually for fast unit testing
        Field lockField = TicketService.class.getDeclaredField("lockService");
        lockField.setAccessible(true);
        lockField.set(ticketService, lockService);

        TicketEventProducer mockProducer = new TicketEventProducer();
        Field prodField = TicketService.class.getDeclaredField("eventProducer");
        prodField.setAccessible(true);
        prodField.set(ticketService, mockProducer);
    }

    @Test
    void testListInitialTickets() {
        List<Ticket> tickets = ticketService.getAllTickets(1L).await().indefinitely();
        assertNotNull(tickets);
        assertFalse(tickets.isEmpty());
        assertTrue(tickets.stream().anyMatch(t -> "VIP-101".equals(t.id)));
    }

    @Test
    void testReserveTicketSuccess() {
        TicketService.TicketReservationResult result = ticketService.reserveTicket("VIP-101", "USER-42", "attendee@test.com")
            .await().indefinitely();
        assertTrue(result.success());
        assertEquals(TicketStatus.LOCKED, result.ticket().status);
        assertEquals("USER-42", result.ticket().lockedByUserId);
    }

    @Test
    void testPurchaseTicketSuccess() {
        // First reserve
        ticketService.reserveTicket("VIP-102", "USER-42", "attendee@test.com").await().indefinitely();

        // Then purchase
        TicketService.TicketPurchaseResult purchaseResult = ticketService.purchaseTicket("VIP-102", "USER-42", "Carlos Silva", "carlos@test.com")
            .await().indefinitely();
        assertTrue(purchaseResult.success());
        assertEquals(TicketStatus.SOLD, purchaseResult.ticket().status);
        assertNotNull(purchaseResult.ticket().qrCode);
        assertTrue(purchaseResult.ticket().qrCode.startsWith("QR-VIP-102"));
    }

    @Test
    void testCheckInFlow() {
        // Buy ticket CAM-201
        ticketService.purchaseTicket("CAM-201", "USER-10", "Maria Souza", "maria@test.com")
            .await().indefinitely();

        // Perform Check-in at Gate A
        CheckInEvent checkIn = ticketService.checkInTicket("CAM-201", "STAFF-01", "PORTAO_A")
            .await().indefinitely();

        assertTrue(checkIn.success());
        assertTrue(checkIn.message().contains("Check-in aprovado"));

        // Attempt duplicate check-in (fraud / double-entry prevention)
        CheckInEvent duplicateCheckIn = ticketService.checkInTicket("CAM-201", "STAFF-01", "PORTAO_A")
            .await().indefinitely();

        assertFalse(duplicateCheckIn.success());
        assertTrue(duplicateCheckIn.message().contains("já utilizado"));
    }

    @Test
    void testCheckInUnsoldTicket() {
        // PIS-401 is available, not sold
        CheckInEvent checkIn = ticketService.checkInTicket("PIS-401", "STAFF-01", "PORTAO_B")
            .await().indefinitely();
        assertFalse(checkIn.success());
        assertTrue(checkIn.message().contains("não está com pagamento aprovado"));
    }
}
