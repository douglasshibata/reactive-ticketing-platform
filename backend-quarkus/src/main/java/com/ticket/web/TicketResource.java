package com.ticket.web;

import com.ticket.domain.Ticket;
import com.ticket.service.TicketService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;

@Path("/api/tickets")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TicketResource {

    private static final Logger LOG = Logger.getLogger(TicketResource.class);

    @Inject
    TicketService ticketService;

    @Inject
    TicketSocket ticketSocket;

    @GET
    public Uni<List<Ticket>> listTickets(@QueryParam("eventId") @DefaultValue("1") Long eventId) {
        return ticketService.getAllTickets(eventId);
    }

    @GET
    @Path("/{ticketId}")
    public Uni<Response> getTicket(@PathParam("ticketId") String ticketId) {
        return ticketService.getTicketById(ticketId)
            .map(ticket -> {
                if (ticket == null) {
                    return Response.status(Response.Status.NOT_FOUND).build();
                }
                return Response.ok(ticket).build();
            });
    }

    /**
     * Reserve (Hold/Lock) a ticket with 10-minute TTL
     */
    @POST
    @Path("/{ticketId}/reserve")
    public Uni<Response> reserveTicket(@PathParam("ticketId") String ticketId, ReserveRequest request) {
        String userId = request != null && request.userId() != null ? request.userId() : "USER-" + System.currentTimeMillis();
        String userEmail = request != null && request.userEmail() != null ? request.userEmail() : "attendee@example.com";

        return ticketService.reserveTicket(ticketId, userId, userEmail)
            .map(result -> {
                if (!result.success()) {
                    return Response.status(Response.Status.CONFLICT)
                        .entity(Map.of("success", false, "message", result.message()))
                        .build();
                }

                // Broadcast TICKET_LOCKED via WebSocket to all other users
                ticketSocket.broadcast("TICKET_LOCKED", Map.of(
                    "ticketId", ticketId,
                    "lockedBy", userId,
                    "lockedUntil", result.ticket().lockedUntil
                ));

                return Response.ok(Map.of(
                    "success", true,
                    "message", result.message(),
                    "ticket", result.ticket()
                )).build();
            });
    }

    /**
     * Finalize ticket purchase after payment validation
     */
    @POST
    @Path("/{ticketId}/purchase")
    public Uni<Response> purchaseTicket(@PathParam("ticketId") String ticketId, PurchaseRequest request) {
        String userId = request != null && request.userId() != null ? request.userId() : "USER-ANON";
        String ownerName = request != null && request.ownerName() != null ? request.ownerName() : "Attendee";
        String ownerEmail = request != null && request.ownerEmail() != null ? request.ownerEmail() : "attendee@example.com";

        return ticketService.purchaseTicket(ticketId, userId, ownerName, ownerEmail)
            .map(result -> {
                if (!result.success()) {
                    return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("success", false, "message", result.message()))
                        .build();
                }

                // Broadcast TICKET_SOLD via WebSocket
                ticketSocket.broadcast("TICKET_SOLD", Map.of(
                    "ticketId", ticketId,
                    "ownerName", ownerName,
                    "status", "SOLD"
                ));

                return Response.ok(Map.of(
                    "success", true,
                    "message", result.message(),
                    "ticket", result.ticket()
                )).build();
            });
    }

    /**
     * Release ticket lock
     */
    @POST
    @Path("/{ticketId}/release")
    public Uni<Response> releaseTicket(@PathParam("ticketId") String ticketId, @QueryParam("userId") String userId) {
        return ticketService.releaseTicket(ticketId, userId != null ? userId : "SYSTEM")
            .map(released -> {
                ticketSocket.broadcast("TICKET_RELEASED", Map.of(
                    "ticketId", ticketId,
                    "status", "AVAILABLE"
                ));
                return Response.ok(Map.of("success", released, "message", "Ticket lock released")).build();
            });
    }

    public record ReserveRequest(String userId, String userEmail) {}
    public record PurchaseRequest(String userId, String ownerName, String ownerEmail, String paymentMethod) {}
}
