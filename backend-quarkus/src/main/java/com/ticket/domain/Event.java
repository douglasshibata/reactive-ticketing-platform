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

    public Event() {}

    public Event(Long id, String title, String description, String venue, LocalDateTime eventDate, Integer totalCapacity, Integer availableSeats) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.venue = venue;
        this.eventDate = eventDate;
        this.totalCapacity = totalCapacity;
        this.availableSeats = availableSeats;
    }
}
