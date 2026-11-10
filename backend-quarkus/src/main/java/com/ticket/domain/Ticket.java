package com.ticket.domain;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tickets")
public class Ticket extends PanacheEntityBase {

    @Id
    public String id; // e.g., "TICK-VIP-101"

    @Column(nullable = false)
    public Long eventId;

    @Column(nullable = false)
    public String seatNumber;

    @Column(nullable = false)
    public String tier; // VIP, CAMAROTE, PISTA, PISTA_PREMIUM

    @Column(nullable = false, precision = 10, scale = 2)
    public BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public TicketStatus status;

    public String lockedByUserId;

    public LocalDateTime lockedUntil;

    public String ownerName;

    public String ownerEmail;

    public String qrCode;

    public LocalDateTime checkedInAt;

    public Ticket() {}

    public Ticket(String id, Long eventId, String seatNumber, String tier, BigDecimal price, TicketStatus status) {
        this.id = id;
        this.eventId = eventId;
        this.seatNumber = seatNumber;
        this.tier = tier;
        this.price = price;
        this.status = status;
    }
}
