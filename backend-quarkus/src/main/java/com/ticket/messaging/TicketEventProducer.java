package com.ticket.messaging;

import com.ticket.domain.TicketOrderEvent;
import io.smallrye.reactive.messaging.annotations.Broadcast;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.jboss.logging.Logger;

@ApplicationScoped
public class TicketEventProducer {

    private static final Logger LOG = Logger.getLogger(TicketEventProducer.class);

    @Inject
    @Channel("ticket-reserved-out")
    @Broadcast
    Emitter<TicketOrderEvent> reservationEmitter;

    @Inject
    @Channel("ticket-paid-out")
    @Broadcast
    Emitter<TicketOrderEvent> paidEmitter;

    public void emitTicketReserved(TicketOrderEvent event) {
        LOG.infof("Kafka Producer: Emitting ticket reservation event for ticket=%s, user=%s",
            event.ticketId(), event.userId());
        try {
            if (reservationEmitter != null) {
                reservationEmitter.send(event);
            }
        } catch (Exception e) {
            LOG.warnf("Could not emit to Kafka topic ticket.reserved: %s", e.getMessage());
        }
    }

    public void emitTicketPaid(TicketOrderEvent event) {
        LOG.infof("Kafka Producer: Emitting ticket paid event for ticket=%s, user=%s",
            event.ticketId(), event.userId());
        try {
            if (paidEmitter != null) {
                paidEmitter.send(event);
            }
        } catch (Exception e) {
            LOG.warnf("Could not emit to Kafka topic ticket.paid: %s", e.getMessage());
        }
    }
}
