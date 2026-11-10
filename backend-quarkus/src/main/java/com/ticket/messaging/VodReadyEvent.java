package com.ticket.messaging;

public record VodReadyEvent(
    String eventId,
    String eventName,
    String videoUrl
) {}
