package com.ticket.web;

import com.ticket.domain.Event;
import com.ticket.domain.Ticket;
import com.ticket.service.TicketService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Path("/api/events")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EventResource {

    @Inject
    TicketService ticketService;

    @GET
    public Uni<List<Event>> listEvents() {
        Event festival = new Event(
            1L,
            "Mega Rock Festival 2026",
            "O maior festival de música e tecnologia reativo do Brasil.",
            "Allianz Parque, São Paulo",
            LocalDateTime.now().plusMonths(1),
            50000,
            49992
        );
        return Uni.createFrom().item(List.of(festival));
    }

    @GET
    @Path("/{eventId}/inventory")
    public Uni<Response> getEventInventory(@PathParam("eventId") Long eventId) {
        return ticketService.getAllTickets(eventId)
            .map(tickets -> {
                long available = tickets.stream().filter(t -> "AVAILABLE".equals(t.status.name())).count();
                long locked = tickets.stream().filter(t -> "LOCKED".equals(t.status.name())).count();
                long sold = tickets.stream().filter(t -> "SOLD".equals(t.status.name()) || "CHECKED_IN".equals(t.status.name())).count();
                long checkedIn = tickets.stream().filter(t -> "CHECKED_IN".equals(t.status.name())).count();

                return Response.ok(Map.of(
                    "eventId", eventId,
                    "totalSeats", tickets.size(),
                    "available", available,
                    "locked", locked,
                    "sold", sold,
                    "checkedIn", checkedIn,
                    "tickets", tickets
                )).build();
            });
    }
}
