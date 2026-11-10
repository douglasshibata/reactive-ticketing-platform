package com.ticket.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TicketOrderEvent(
    String ticketId,
    Long eventId,
    String userId,
    String userEmail,
    BigDecimal amount,
    String eventType, // TICKET_RESERVED, TICKET_PAID, TICKET_RELEASED
    LocalDateTime timestamp
) {}
