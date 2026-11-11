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
    public S3Presigner presigner; // O Quarkus injeta e gere o ciclo de vida automaticamente

    @ConfigProperty(name = "aws.s3.vod.bucket-name", defaultValue = "empresa-vod-archive-prd")
    public String bucketName;

    /**
     * Valida o bilhete e gera o link seguro.
     */
    public Uni<VodAccessResponse> generateSecureUrl(String eventId, String userId) {
        Long parsedEventId;
        try {
            parsedEventId = Long.parseLong(eventId);
        } catch (NumberFormatException e) {
            return Uni.createFrom().failure(new SecurityException("ID de evento inválido."));
        }

        // 1. Validação de Regra de Negócio (O utilizador comprou o bilhete?)
        return Ticket.<Ticket>find(
                "(eventId = ?1 or (event is not null and event.id = ?1)) and (userId = ?2 or lockedByUserId = ?2) and (status = ?3 or status = ?4)",
                parsedEventId,
                userId,
                TicketStatus.SOLD,
                TicketStatus.CHECKED_IN
            )
            .firstResult()
            .onItem().ifNotNull().transformToUni(ticket -> {
                // 2. Busca a URL ou chave do vídeo no Evento associado
                if (ticket.event != null && ticket.event.vodS3Url != null) {
                    return Uni.createFrom().item(ticket.event.vodS3Url);
                }
                // Se a entidade ticket não estiver ligada no banco relacional direto, busca o Evento
                return Event.<Event>findById(parsedEventId)
                    .map(ev -> (ev != null && ev.vodS3Url != null) ? ev.vodS3Url : "recordings/event-" + eventId + "/master.mp4");
            })
            .onItem().ifNotNull().transform(videoKey -> {
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
            // Se não encontrou o bilhete válido, lança exceção (traduzida para 403 Forbidden no REST)
            .onItem().ifNull().failWith(() -> new SecurityException("Sem acesso ao VOD."));
    }
}
