package com.ticket.web;

import com.ticket.service.VodAccessResponse;
import com.ticket.service.VodAccessService;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Path("/api/events")
@Produces(MediaType.APPLICATION_JSON)
public class VodResource {

    @Inject
    public VodAccessService vodAccessService;

    @Inject
    public JsonWebToken jwt; // Injeção automática do Bearer Token do utilizador (OIDC/Keycloak)

    @GET
    @Path("/{eventId}/vod-access")
    @RolesAllowed("USER") // O Quarkus bloqueia acessos anónimos automaticamente
    public Uni<Response> getVodAccess(@PathParam("eventId") String eventId) {

        // Extraímos o ID do utilizador que fez login (subject do JWT)
        String userId = (jwt != null && jwt.getSubject() != null) ? jwt.getSubject() : "anonymous";

        return vodAccessService.generateSecureUrl(eventId, userId)
                .map(response -> Response.ok(response).build())
                .onFailure(SecurityException.class)
                .recoverWithItem(Response.status(Response.Status.FORBIDDEN).build());
    }
}
