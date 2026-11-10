package com.ticket.domain;

import java.time.LocalDateTime;

public record CheckInEvent(
    String ticketId,
    Long eventId,
    String staffUserId,
    String gate,
    boolean success,
    String message,
    LocalDateTime timestamp
) {}
