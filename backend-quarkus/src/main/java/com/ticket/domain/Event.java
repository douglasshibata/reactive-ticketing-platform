package com.ticket.domain;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "events")
public class Event extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public String title;

    @Column(columnDefinition = "TEXT")
    public String description;

    @Column(nullable = false)
    public String venue;

    @Column(nullable = false)
    public LocalDateTime eventDate;

    @Column(nullable = false)
    public Integer totalCapacity;

    @Column(nullable = false)
    public Integer availableSeats;

    public String roomId;       // O identificador único da sala Jitsi

    public String status;       // SCHEDULED, LIVE, VOD_READY

    public String vodS3Url;     // A rota final do vídeo gravado

    public Event() {}

    public Event(Long id, String title, String description, String venue, LocalDateTime eventDate, Integer totalCapacity, Integer availableSeats) {
        this(id, title, description, venue, eventDate, totalCapacity, availableSeats, "room-" + id, "SCHEDULED", null);
    }

    public Event(Long id, String title, String description, String venue, LocalDateTime eventDate, Integer totalCapacity, Integer availableSeats, String roomId, String status, String vodS3Url) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.venue = venue;
        this.eventDate = eventDate;
        this.totalCapacity = totalCapacity;
        this.availableSeats = availableSeats;
        this.status = "LIVE";
    }

    public Event(Long id, String title, String description, String venue, LocalDateTime eventDate, Integer totalCapacity, Integer availableSeats, String roomId, String status, String vodS3Url) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.venue = venue;
        this.eventDate = eventDate;
        this.totalCapacity = totalCapacity;
        this.availableSeats = availableSeats;
        this.roomId = roomId;
        this.status = status;
        this.vodS3Url = vodS3Url;
    }
}
