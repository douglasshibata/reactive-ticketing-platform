package com.ticket.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

public class VodAccessServiceTest {

    private VodAccessService service;
    private S3Presigner presigner;

    @BeforeEach
    void setUp() {
        service = new VodAccessService();
        presigner = S3Presigner.builder()
                .region(Region.SA_EAST_1)
                .endpointOverride(URI.create("http://localhost:4566"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test_key", "test_secret")
                ))
                .build();

        service.setPresigner(presigner);
        service.setBucketName("test-vod-bucket");
    }

    @Test
    void testInvalidEventIdFormat() {
        assertThrows(IllegalArgumentException.class, () -> {
            service.generateSecureUrl("invalid-id", "USER-1").await().indefinitely();
        });
    }

    @Test
    void testPresignerGeneratesUrlLocallyWithoutNetwork() {
        // Test direct local presigning fast execution
        software.amazon.awssdk.services.s3.model.GetObjectRequest getObjectRequest =
                software.amazon.awssdk.services.s3.model.GetObjectRequest.builder()
                        .bucket("test-vod-bucket")
                        .key("recordings/event101/video.mp4")
                        .build();

        software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest presignRequest =
                software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest.builder()
                        .signatureDuration(java.time.Duration.ofHours(3))
                        .getObjectRequest(getObjectRequest)
                        .build();

        software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest presigned =
                presigner.presignGetObject(presignRequest);

        assertNotNull(presigned);
        assertNotNull(presigned.url());
        assertTrue(presigned.url().toString().contains("test-vod-bucket"));
        assertTrue(presigned.url().toString().contains("X-Amz-Signature"));
    }
}
