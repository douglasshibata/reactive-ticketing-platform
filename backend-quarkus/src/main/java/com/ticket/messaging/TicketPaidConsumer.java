package com.ticket.messaging;

import com.ticket.domain.TicketOrderEvent;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

@ApplicationScoped
public class TicketPaidConsumer {

    private static final Logger LOG = Logger.getLogger(TicketPaidConsumer.class);

    @Incoming("ticket-paid-in")
    public Uni<Void> consumePaidTicket(TicketOrderEvent event) {
        LOG.infof("Kafka Consumer [ticket-paid-in]: Async processing for ticket=%s, userEmail=%s, amount=%s",
            event.ticketId(), event.userEmail(), event.amount());

        // Simulates async QR Code generation, PDF ticket invoice rendering, and email dispatch
        return Uni.createFrom().item(event)
            .onItem().invoke(e -> {
                String generatedQrCode = "QR-" + e.ticketId() + "-" + System.currentTimeMillis();
                LOG.infof("Generated QR code: %s for attendee email: %s. PDF Ticket dispatched.",
                    generatedQrCode, e.userEmail());
            })
            .replaceWithVoid();
    }
}
