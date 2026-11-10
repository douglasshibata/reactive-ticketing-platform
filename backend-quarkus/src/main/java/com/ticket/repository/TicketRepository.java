package com.ticket.repository;

import com.ticket.domain.Ticket;
import com.ticket.domain.TicketStatus;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class TicketRepository implements PanacheRepositoryBase<Ticket, String> {

    public Uni<List<Ticket>> findByEventId(Long eventId) {
        return list("eventId", eventId);
    }

    public Uni<List<Ticket>> findAvailableByEventId(Long eventId) {
        return list("eventId = ?1 and status = ?2", eventId, TicketStatus.AVAILABLE);
    }

    public Uni<Long> countCheckedInByEventId(Long eventId) {
        return count("eventId = ?1 and status = ?2", eventId, TicketStatus.CHECKED_IN);
    }

    public Uni<Long> countSoldByEventId(Long eventId) {
        return count("eventId = ?1 and (status = ?2 or status = ?3)", eventId, TicketStatus.SOLD, TicketStatus.CHECKED_IN);
    }
}
