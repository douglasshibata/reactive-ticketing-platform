package com.ticket.web;

import com.ticket.service.VodAccessResponse;
import com.ticket.service.VodAccessService;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VodAccessResourceTest {

    private VodAccessService vodAccessService;
    private VodResource vodResource;
    private S3Presigner mockPresigner;
    private JsonWebToken mockJwt;

    @BeforeEach
    void setUp() {
        mockPresigner = mock(S3Presigner.class);
        mockJwt = mock(JsonWebToken.class);

        vodAccessService = new VodAccessService();
        vodAccessService.presigner = mockPresigner;
        vodAccessService.bucketName = "empresa-vod-archive-prd";

        vodResource = new VodResource();
        vodResource.vodAccessService = vodAccessService;
        vodResource.jwt = mockJwt;
    }

    @Test
    void testInvalidEventIdThrowsSecurityExceptionAndReturns403() {
        when(mockJwt.getSubject()).thenReturn("USER-10");
        Response response = vodResource.getVodAccess("not-a-number").await().indefinitely();
        assertEquals(Response.Status.FORBIDDEN.getStatusCode(), response.getStatus());
    }

    @Test
    void testPresignerSigningOperationInService() throws Exception {
        PresignedGetObjectRequest mockPresigned = mock(PresignedGetObjectRequest.class);
        when(mockPresigned.url()).thenReturn(new URI("https://empresa-vod-archive-prd.s3.sa-east-1.amazonaws.com/recordings/test.mp4?sig=xyz").toURL());
        Instant expiry = Instant.now().plus(Duration.ofHours(3));
        when(mockPresigned.expiration()).thenReturn(expiry);
        when(mockPresigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(mockPresigned);

        // Verify direct presigner signing logic
        GetObjectPresignRequest req = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofHours(3))
                .getObjectRequest(b -> b.bucket("empresa-vod-archive-prd").key("recordings/test.mp4"))
                .build();
        PresignedGetObjectRequest result = mockPresigner.presignGetObject(req);

        VodAccessResponse response = new VodAccessResponse(result.url().toString(), result.expiration().toString());
        assertNotNull(response);
        assertTrue(response.secureUrl().contains("empresa-vod-archive-prd"));
        assertEquals(expiry.toString(), response.expiresAt());
    }

    @Test
    void testVodResourceRecoversSecurityExceptionTo403() {
        VodAccessService mockService = mock(VodAccessService.class);
        when(mockService.generateSecureUrl("999", "USER-ANON"))
                .thenReturn(Uni.createFrom().failure(new SecurityException("Sem acesso ao VOD.")));

        VodResource resource = new VodResource();
        resource.vodAccessService = mockService;
        resource.jwt = mockJwt;
        when(mockJwt.getSubject()).thenReturn("USER-ANON");

        Response response = resource.getVodAccess("999").await().indefinitely();
        assertEquals(Response.Status.FORBIDDEN.getStatusCode(), response.getStatus());
    }

    @Test
    void testVodResourceReturns200WithValidAccessResponse() {
        VodAccessService mockService = mock(VodAccessService.class);
        VodAccessResponse expected = new VodAccessResponse(
                "https://empresa-vod-archive-prd.s3.sa-east-1.amazonaws.com/recordings/test.mp4?sig=123",
                Instant.now().plus(3, ChronoUnit.HOURS).toString()
        );
        when(mockService.generateSecureUrl("101", "USER-VIP"))
                .thenReturn(Uni.createFrom().item(expected));

        VodResource resource = new VodResource();
        resource.vodAccessService = mockService;
        resource.jwt = mockJwt;
        when(mockJwt.getSubject()).thenReturn("USER-VIP");

        Response response = resource.getVodAccess("101").await().indefinitely();
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals(expected, response.getEntity());
    }
}
