package com.ticket.web.dto;

public record JibriWebhookPayload(
    String roomId,
    String s3Url,
    String status
) {}
