package com.ticket.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket endpoint for real-time ticket inventory and check-in updates.
 * Clients connect to ws://localhost:8080/ws/tickets
 */
@WebSocket(path = "/ws/tickets")
@ApplicationScoped
public class TicketSocket {

    private static final Logger LOG = Logger.getLogger(TicketSocket.class);

    private final Set<WebSocketConnection> connections = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Inject
    ObjectMapper objectMapper;

    @OnOpen
    public void onOpen(WebSocketConnection connection) {
        connections.add(connection);
        LOG.infof("WebSocket client connected: %s (Total active: %d)", connection.id(), connections.size());

        // Welcome notification
        broadcastToConnection(connection, new WsMessage("CONNECTED", "Subscribed to live ticket stream", null));
    }

    @OnClose
    public void onClose(WebSocketConnection connection) {
        connections.remove(connection);
        LOG.infof("WebSocket client disconnected: %s (Total active: %d)", connection.id(), connections.size());
    }

    @OnTextMessage
    public void onMessage(String message, WebSocketConnection connection) {
        LOG.infof("Received message from client %s: %s", connection.id(), message);
        // Echo or process client ping
        if ("ping".equalsIgnoreCase(message.trim())) {
            connection.sendText("pong");
        }
    }

    /**
     * Broadcasts an inventory event (e.g. TICKET_LOCKED, TICKET_RELEASED, TICKET_SOLD, CHECK_IN_SUCCESS)
     * to all connected clients.
     */
    public void broadcast(String eventType, Object payload) {
        WsMessage msg = new WsMessage(eventType, "Event emitted", payload);
        try {
            String json = objectMapper.writeValueAsString(msg);
            for (WebSocketConnection conn : connections) {
                if (conn.isOpen()) {
                    conn.sendText(json);
                }
            }
            LOG.infof("Broadcasted event %s to %d clients", eventType, connections.size());
        } catch (Exception e) {
            LOG.errorf("Failed to broadcast message: %s", e.getMessage());
        }
    }

    private void broadcastToConnection(WebSocketConnection conn, WsMessage msg) {
        try {
            if (conn.isOpen()) {
                conn.sendText(objectMapper.writeValueAsString(msg));
            }
        } catch (Exception e) {
            LOG.warnf("Failed to send welcome message: %s", e.getMessage());
        }
    }

    public int getActiveConnectionCount() {
        return connections.size();
    }

    public record WsMessage(String type, String message, Object payload) {}
}
