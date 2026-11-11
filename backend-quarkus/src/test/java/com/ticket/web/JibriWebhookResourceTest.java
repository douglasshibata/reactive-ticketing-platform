package com.ticket.web;

import com.ticket.messaging.VodReadyEvent;
import com.ticket.web.dto.JibriWebhookPayload;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.MutinyEmitter;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JibriWebhookResourceTest {

    private JibriWebhookResource resource;
    private MutinyEmitter<VodReadyEvent> mockEmitter;

    @BeforeEach
    void setUp() {
        resource = new JibriWebhookResource();
        resource.webhookSecret = "super_secret_token";
        mockEmitter = mock(MutinyEmitter.class);
        when(mockEmitter.send(any(VodReadyEvent.class))).thenReturn(Uni.createFrom().voidItem());
        resource.vodEmitter = mockEmitter;
    }

    @Test
    void testUnauthorizedWhenHeaderMissingOrInvalid() {
        JibriWebhookPayload payload = new JibriWebhookPayload("room-101", "s3://recordings/video.mp4", "FINISHED");

        // Missing Authorization Header
        Response response1 = resource.handleJibriUploadSuccess(null, payload).await().indefinitely();
        assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response1.getStatus());

        // Incorrect Bearer Token
        Response response2 = resource.handleJibriUploadSuccess("Bearer wrong_secret", payload).await().indefinitely();
        assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response2.getStatus());
    }

    @Test
    void testBadRequestWhenPayloadInvalid() {
        String auth = "Bearer super_secret_token";
        Response response = resource.handleJibriUploadSuccess(auth, null).await().indefinitely();
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());

        Response response2 = resource.handleJibriUploadSuccess(auth, new JibriWebhookPayload(null, "s3://...", "OK")).await().indefinitely();
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response2.getStatus());
    }

    @Test
    void testSecurityValidationRejectsUntrustedRequests() {
        String authWrong = "Bearer invalid";
        JibriWebhookPayload payload = new JibriWebhookPayload("room-101", "s3://recordings/video.mp4", "FINISHED");
        Response response = resource.handleJibriUploadSuccess(authWrong, payload).await().indefinitely();
        assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
        Mockito.verifyNoInteractions(mockEmitter);
    }
}
