package com.ticket.service;

import com.ticket.domain.CheckInEvent;
import com.ticket.domain.Ticket;
import com.ticket.domain.TicketOrderEvent;
import com.ticket.domain.TicketStatus;
import com.ticket.messaging.TicketEventProducer;
import com.ticket.repository.TicketRepository;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class TicketService {

    private static final Logger LOG = Logger.getLogger(TicketService.class);

    @Inject
    TicketRepository ticketRepository;

    @Inject
    LockService lockService;

    @Inject
    TicketEventProducer eventProducer;

    // In-memory fallback / cache for ticket catalog in standalone/test mode
    private final Map<String, Ticket> mockInventory = new ConcurrentHashMap<>();

    public TicketService() {
        seedInitialInventory();
    }

    private void seedInitialInventory() {
        addMock(new Ticket("VIP-101", 1L, "A-01", "VIP", new java.math.BigDecimal("350.00"), TicketStatus.AVAILABLE));
        addMock(new Ticket("VIP-102", 1L, "A-02", "VIP", new java.math.BigDecimal("350.00"), TicketStatus.AVAILABLE));
        addMock(new Ticket("CAM-201", 1L, "B-01", "CAMAROTE", new java.math.BigDecimal("220.00"), TicketStatus.AVAILABLE));
        addMock(new Ticket("CAM-202", 1L, "B-02", "CAMAROTE", new java.math.BigDecimal("220.00"), TicketStatus.AVAILABLE));
        addMock(new Ticket("PIS-301", 1L, "C-01", "PISTA_PREMIUM", new java.math.BigDecimal("180.00"), TicketStatus.AVAILABLE));
        addMock(new Ticket("PIS-302", 1L, "C-02", "PISTA_PREMIUM", new java.math.BigDecimal("180.00"), TicketStatus.AVAILABLE));
        addMock(new Ticket("PIS-401", 1L, "D-01", "PISTA", new java.math.BigDecimal("90.00"), TicketStatus.AVAILABLE));
        addMock(new Ticket("PIS-402", 1L, "D-02", "PISTA", new java.math.BigDecimal("90.00"), TicketStatus.AVAILABLE));
    }

    private void addMock(Ticket t) {
        mockInventory.put(t.id, t);
    }

    public Uni<List<Ticket>> getAllTickets(Long eventId) {
        return Uni.createFrom().item(() -> {
            return mockInventory.values().stream()
                .filter(t -> eventId == null || t.eventId.equals(eventId))
                .toList();
        });
    }

    public Uni<Ticket> getTicketById(String ticketId) {
        return Uni.createFrom().item(() -> mockInventory.get(ticketId));
    }

    /**
     * Attempts to reserve (lock) a ticket for 10 minutes.
     * Uses Redis atomic SETNX lock. If already locked, returns conflict.
     */
    public Uni<TicketReservationResult> reserveTicket(String ticketId, String userId, String userEmail) {
        Ticket ticket = mockInventory.get(ticketId);
        if (ticket == null) {
            return Uni.createFrom().item(new TicketReservationResult(false, "Ticket not found", null));
        }

        if (ticket.status == TicketStatus.SOLD || ticket.status == TicketStatus.CHECKED_IN) {
            return Uni.createFrom().item(new TicketReservationResult(false, "Ticket already sold", null));
        }

        return lockService.acquireLock(ticketId, userId, LockService.DEFAULT_HOLD_DURATION)
            .flatMap(acquired -> {
                if (!acquired) {
                    return Uni.createFrom().item(new TicketReservationResult(false, "Ticket currently locked by another user", null));
                }

                ticket.status = TicketStatus.LOCKED;
                ticket.lockedByUserId = userId;
                ticket.ownerEmail = userEmail;
                ticket.lockedUntil = LocalDateTime.now().plus(LockService.DEFAULT_HOLD_DURATION);

                // Publish reservation event to Kafka
                TicketOrderEvent event = new TicketOrderEvent(
                    ticketId,
                    ticket.eventId,
                    userId,
                    userEmail,
                    ticket.price,
                    "TICKET_RESERVED",
                    LocalDateTime.now()
                );
                eventProducer.emitTicketReserved(event);

                return Uni.createFrom().item(new TicketReservationResult(true, "Ticket locked successfully for 10 minutes", ticket));
            });
    }

    /**
     * Finalizes purchase after payment confirmation.
     * Emits ticket.paid event to Kafka for async PDF & QR code generation.
     */
    public Uni<TicketPurchaseResult> purchaseTicket(String ticketId, String userId, String ownerName, String ownerEmail) {
        Ticket ticket = mockInventory.get(ticketId);
        if (ticket == null) {
            return Uni.createFrom().item(new TicketPurchaseResult(false, "Ticket not found", null));
        }

        // Must be locked by this user or available
        if (ticket.status == TicketStatus.LOCKED && !userId.equals(ticket.lockedByUserId)) {
            return Uni.createFrom().item(new TicketPurchaseResult(false, "Ticket is locked by another attendee", null));
        }

        if (ticket.status == TicketStatus.SOLD || ticket.status == TicketStatus.CHECKED_IN) {
            return Uni.createFrom().item(new TicketPurchaseResult(false, "Ticket has already been purchased", null));
        }

        ticket.status = TicketStatus.SOLD;
        ticket.ownerName = ownerName;
        ticket.ownerEmail = ownerEmail;
        ticket.qrCode = "QR-" + ticket.id + "-" + System.currentTimeMillis();
        ticket.lockedByUserId = null;
        ticket.lockedUntil = null;

        // Release Redis lock
        return lockService.releaseLock(ticketId, userId)
            .map(released -> {
                // Publish purchase event to Kafka
                TicketOrderEvent event = new TicketOrderEvent(
                    ticketId,
                    ticket.eventId,
                    userId,
                    ownerEmail,
                    ticket.price,
                    "TICKET_PAID",
                    LocalDateTime.now()
                );
                eventProducer.emitTicketPaid(event);

                return new TicketPurchaseResult(true, "Ticket purchased successfully", ticket);
            });
    }

    /**
     * Releases lock manually or on timeout.
     */
    public Uni<Boolean> releaseTicket(String ticketId, String userId) {
        Ticket ticket = mockInventory.get(ticketId);
        if (ticket != null && ticket.status == TicketStatus.LOCKED) {
            ticket.status = TicketStatus.AVAILABLE;
            ticket.lockedByUserId = null;
            ticket.lockedUntil = null;
        }
        return lockService.releaseLock(ticketId, userId);
    }

    /**
     * Staff QR Code check-in verification at event entrance.
     * Response target: sub-100ms.
     */
    public Uni<CheckInEvent> checkInTicket(String ticketId, String staffUserId, String gate) {
        Ticket ticket = mockInventory.get(ticketId);
        LocalDateTime now = LocalDateTime.now();

        if (ticket == null) {
            return Uni.createFrom().item(new CheckInEvent(ticketId, null, staffUserId, gate, false, "Ingresso inexistente", now));
        }

        if (ticket.status == TicketStatus.CHECKED_IN) {
            return Uni.createFrom().item(new CheckInEvent(ticketId, ticket.eventId, staffUserId, gate, false, "Alerta: Ingresso já utilizado anteriormente em " + ticket.checkedInAt, now));
        }

        if (ticket.status != TicketStatus.SOLD) {
            return Uni.createFrom().item(new CheckInEvent(ticketId, ticket.eventId, staffUserId, gate, false, "Ingresso não está com pagamento aprovado (Status: " + ticket.status + ")", now));
        }

        ticket.status = TicketStatus.CHECKED_IN;
        ticket.checkedInAt = now;

        return Uni.createFrom().item(new CheckInEvent(ticketId, ticket.eventId, staffUserId, gate, true, "Check-in aprovado! Bem-vindo " + (ticket.ownerName != null ? ticket.ownerName : ""), now));
    }

    public record TicketReservationResult(boolean success, String message, Ticket ticket) {}
    public record TicketPurchaseResult(boolean success, String message, Ticket ticket) {}
}
