package com.ticket.repository;

import com.ticket.domain.Event;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class EventRepository implements PanacheRepository<Event> {

    public Uni<Event> findByIdReactive(Long id) {
        return findById(id);
    }
}
