package com.ticket.web;

import com.ticket.service.VodAccessResponse;
import com.ticket.service.VodAccessService;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Path("/api/events")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class VodResource {

    @Inject
    VodAccessService vodAccessService;

    @Inject
    Instance<JsonWebToken> jwtInstance; // Permite injeção com fallback graceful

    @GET
    @Path("/{eventId}/vod-access")
    public Uni<Response> getVodAccess(
            @PathParam("eventId") String eventId,
            @Context HttpHeaders headers,
            @QueryParam("userId") String queryUserId) {

        // Extrai o ID do utilizador autenticado via JWT ou query/header de contingência
        String userId = null;
        if (jwtInstance.isResolvable()) {
            JsonWebToken jwt = jwtInstance.get();
            if (jwt != null && jwt.getName() != null) {
                userId = jwt.getSubject() != null ? jwt.getSubject() : jwt.getName();
            }
        }

        if (userId == null && headers != null) {
            String customUser = headers.getHeaderString("X-User-Id");
            if (customUser != null && !customUser.isBlank()) {
                userId = customUser;
            }
        }

        if (userId == null && queryUserId != null && !queryUserId.isBlank()) {
            userId = queryUserId;
        }

        if (userId == null) {
            return Uni.createFrom().item(Response.status(Response.Status.UNAUTHORIZED)
                .entity("{\"error\": \"Utilizador não autenticado\"}").build());
        }

        return vodAccessService.generateSecureUrl(eventId, userId)
                .map(response -> Response.ok(response).build())
                .onFailure(SecurityException.class)
                .recoverWithItem(Response.status(Response.Status.FORBIDDEN)
                    .entity("{\"error\": \"Sem acesso ao VOD. Bilhete válido não encontrado.\"}").build())
                .onFailure(IllegalArgumentException.class)
                .recoverWithItem(Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"ID do evento inválido\"}").build());
    }
}
