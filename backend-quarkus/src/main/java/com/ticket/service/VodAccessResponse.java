package com.ticket.service;

public record VodAccessResponse(
    String secureUrl,
    String expiresAt
) {}
