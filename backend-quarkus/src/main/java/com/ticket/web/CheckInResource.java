package com.ticket.web;

import com.ticket.domain.CheckInEvent;
import com.ticket.service.TicketService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Path("/api/check-in")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CheckInResource {

    @Inject
    TicketService ticketService;

    @Inject
    TicketSocket ticketSocket;

    /**
     * Real-time gate credentialing scanner (<100ms response)
     */
    @POST
    @Path("/scan")
    public Uni<Response> scanQrCode(ScanRequest request) {
        if (request == null || request.ticketId() == null) {
            return Uni.createFrom().item(
                Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("success", false, "message", "QR Code / Ticket ID inválido"))
                    .build()
            );
        }

        String staffUserId = request.staffUserId() != null ? request.staffUserId() : "GATE-STAFF-01";
        String gate = request.gate() != null ? request.gate() : "PORTAO_A";

        return ticketService.checkInTicket(request.ticketId(), staffUserId, gate)
            .map(event -> {
                if (event.success()) {
                    // Broadcast CHECK_IN_SUCCESS event to live dashboards
                    ticketSocket.broadcast("CHECK_IN_SUCCESS", Map.of(
                        "ticketId", event.ticketId(),
                        "eventId", event.eventId(),
                        "gate", event.gate(),
                        "timestamp", event.timestamp()
                    ));
                }

                return Response.ok(event).build();
            });
    }

    /**
     * Offline batch synchronization endpoint
     * Used by tablets when re-establishing internet connection after offline gate scanning
     */
    @POST
    @Path("/batch-sync")
    public Uni<Response> batchSync(BatchSyncRequest request) {
        if (request == null || request.scans() == null || request.scans().isEmpty()) {
            return Uni.createFrom().item(Response.ok(Map.of("synced", 0, "results", List.of())).build());
        }

        List<Uni<CheckInEvent>> unis = new ArrayList<>();
        for (ScanRequest scan : request.scans()) {
            unis.add(ticketService.checkInTicket(scan.ticketId(), scan.staffUserId(), scan.gate()));
        }

        return Uni.join().all(unis).andCollectFailures()
            .map(results -> {
                long successCount = results.stream().filter(CheckInEvent::success).count();
                ticketSocket.broadcast("BATCH_SYNC_COMPLETED", Map.of(
                    "totalProcessed", results.size(),
                    "successfulCheckIns", successCount
                ));

                return Response.ok(Map.of(
                    "totalProcessed", results.size(),
                    "successfulCheckIns", successCount,
                    "details", results
                )).build();
            });
    }

    public record ScanRequest(String ticketId, String staffUserId, String gate) {}
    public record BatchSyncRequest(List<ScanRequest> scans) {}
}
