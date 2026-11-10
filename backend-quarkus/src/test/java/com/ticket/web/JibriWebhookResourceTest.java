package com.ticket.web;

import com.ticket.messaging.VodReadyEvent;
import com.ticket.web.dto.JibriWebhookPayload;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.Cancellable;
import io.smallrye.reactive.messaging.MutinyEmitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class JibriWebhookResourceTest {

    private JibriWebhookResource resource;
    private AtomicReference<VodReadyEvent> emittedEvent;

    @BeforeEach
    void setUp() throws Exception {
        resource = new JibriWebhookResource();
        emittedEvent = new AtomicReference<>();

        Field secretField = JibriWebhookResource.class.getDeclaredField("webhookSecret");
        secretField.setAccessible(true);
        secretField.set(resource, "test_secret_123");

        // Mock emitter
        MutinyEmitter<VodReadyEvent> mockEmitter = new MutinyEmitter<>() {
            @Override
            public Uni<Void> send(VodReadyEvent msg) {
                emittedEvent.set(msg);
                return Uni.createFrom().voidItem();
            }

            @Override
            public void sendAndAwait(VodReadyEvent msg) {
                emittedEvent.set(msg);
            }

            @Override
            public Cancellable sendAndForget(VodReadyEvent msg) {
                emittedEvent.set(msg);
                return () -> {};
            }

            @Override
            public <M extends Message<? extends VodReadyEvent>> void send(M msg) {}

            @Override
            public <M extends Message<? extends VodReadyEvent>> Uni<Void> sendMessage(M msg) {
                return Uni.createFrom().voidItem();
            }

            @Override
            public <M extends Message<? extends VodReadyEvent>> void sendMessageAndAwait(M msg) {}

            @Override
            public <M extends Message<? extends VodReadyEvent>> Cancellable sendMessageAndForget(M msg) {
                return () -> {};
            }

            @Override
            public void complete() {}

            @Override
            public void error(Exception e) {}

            @Override
            public boolean isCancelled() { return false; }

            @Override
            public boolean hasRequests() { return true; }
        };

        Field emitterField = JibriWebhookResource.class.getDeclaredField("vodEmitter");
        emitterField.setAccessible(true);
        emitterField.set(resource, mockEmitter);
    }

    @Test
    void testUnauthorizedWhenHeaderMissingOrInvalid() {
        JibriWebhookPayload payload = new JibriWebhookPayload("jitsi-room-101", "s3://recordings/video.mp4", "SUCCESS");

        Response respNull = resource.handleJibriUploadSuccess(null, payload).await().indefinitely();
        assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), respNull.getStatus());

        Response respInvalid = resource.handleJibriUploadSuccess("Bearer wrong_secret", payload).await().indefinitely();
        assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), respInvalid.getStatus());
    }

    @Test
    void testBadRequestWhenPayloadEmpty() {
        Response resp = resource.handleJibriUploadSuccess("Bearer test_secret_123", null).await().indefinitely();
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), resp.getStatus());
    }
}
