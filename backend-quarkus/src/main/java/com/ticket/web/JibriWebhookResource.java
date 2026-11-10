package com.ticket.web;

import com.ticket.domain.Event;
import com.ticket.messaging.VodReadyEvent;
import com.ticket.web.dto.JibriWebhookPayload;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.MutinyEmitter;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Channel;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/webhooks/jibri")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class JibriWebhookResource {

    // Lê o segredo do ficheiro de configuração (application.properties) ou Variável de Ambiente
    @ConfigProperty(name = "webhook.jibri.secret", defaultValue = "default_dev_secret_only")
    String webhookSecret;

    @Inject
    @Channel("vod-ready")
    MutinyEmitter<VodReadyEvent> vodEmitter; // Emissor reativo para o Kafka

    @POST
    @Path("/upload-success")
    public Uni<Response> handleJibriUploadSuccess(
            @HeaderParam("Authorization") String authHeader,
            JibriWebhookPayload payload) {

        // 1. Barreira de Segurança (Authentication Check com comparação em tempo constante)
        String expectedToken = "Bearer " + webhookSecret;
        if (authHeader == null || !java.security.MessageDigest.isEqual(
                authHeader.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                expectedToken.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            // Rejeita silenciosamente tentativas de intrusão
            return Uni.createFrom().item(Response.status(Response.Status.UNAUTHORIZED).build());
        }

        if (payload == null || payload.roomId() == null) {
            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST).build());
        }

        // 2. Transação Reativa na Base de Dados
        return Panache.withTransaction(() ->
            Event.<Event>find("roomId", payload.roomId()).firstResult()
                .onItem().ifNotNull().transform(event -> {
                    // 3. Atualiza o estado do evento para VOD
                    event.status = "VOD_READY";
                    event.vodS3Url = payload.s3Url();
                    return event;
                })
        )
        // 4. Se a gravação no Postgres correu bem, emite o evento para o Kafka
        .onItem().ifNotNull().transformToUni(event -> {
            VodReadyEvent kafkaEvent = new VodReadyEvent(
                String.valueOf(event.id),
                event.title != null ? event.title : "Evento VOD",
                event.vodS3Url
            );

            // Envia para o Kafka e mapeia a resposta HTTP no final
            return vodEmitter.send(kafkaEvent)
                .replaceWith(Response.ok("{\"message\": \"VOD atualizado e notificado\"}").build());
        })
        // 5. Se o evento não existia na base de dados
        .onItem().ifNull().continueWith(() ->
            Response.status(Response.Status.NOT_FOUND).build()
        );
    }
}
