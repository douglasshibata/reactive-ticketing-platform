package com.ticket.service;

import com.ticket.domain.Event;
import com.ticket.domain.Ticket;
import com.ticket.domain.TicketStatus;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.time.Duration;

@ApplicationScoped
public class VodAccessService {

    @Inject
    S3Presigner presigner; // O Quarkus injeta e gere o ciclo de vida automaticamente

    @ConfigProperty(name = "aws.s3.vod.bucket-name", defaultValue = "empresa-vod-archive-prd")
    String bucketName;

    /**
     * Valida o bilhete e gera o link seguro.
     */
    public Uni<VodAccessResponse> generateSecureUrl(String eventIdStr, String userId) {
        Long eventId;
        try {
            eventId = Long.parseLong(eventIdStr);
        } catch (NumberFormatException e) {
            return Uni.createFrom().failure(new IllegalArgumentException("Formato inválido de ID do evento"));
        }

        // 1. Validação de Regra de Negócio (O utilizador comprou o bilhete para o evento?)
        return Ticket.<Ticket>find(
                "eventId = ?1 and (lockedByUserId = ?2 or ownerEmail = ?2) and (status = ?3 or status = ?4)",
                eventId, userId, TicketStatus.SOLD, TicketStatus.CHECKED_IN
            )
            .firstResult()
            .onItem().ifNotNull().transformToUni(ticket ->
                Event.<Event>findById(eventId)
                    .onItem().ifNotNull().transform(event -> {
                        String videoKey = event.vodS3Url != null ? event.vodS3Url : "recordings/events/" + eventId + "/stream.mp4";

                        // 3. Constrói o pedido base do S3
                        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                                .bucket(bucketName)
                                .key(videoKey)
                                .build();

                        // 4. Cria o pedido de assinatura com TTL restrito (Ex: 3 horas)
                        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                                .signatureDuration(Duration.ofHours(3))
                                .getObjectRequest(getObjectRequest)
                                .build();

                        // 5. Gera a assinatura criptográfica local (Operação super-rápida, em memória)
                        PresignedGetObjectRequest presignedRequest = presigner.presignGetObject(presignRequest);

                        // 6. Retorna o DTO esperado pelo Angular
                        return new VodAccessResponse(
                                presignedRequest.url().toString(),
                                presignedRequest.expiration().toString()
                        );
                    })
                    .onItem().ifNull().failWith(() -> new SecurityException("Sem acesso ao VOD: evento não encontrado."))
            )
            // Se não encontrou o bilhete válido, lança exceção (traduzida para 403 Forbidden no REST)
            .onItem().ifNull().failWith(() -> new SecurityException("Sem acesso ao VOD."));
    }

    public void setPresigner(S3Presigner presigner) {
        this.presigner = presigner;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }
}
