package com.printguard.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ModelGenerationWebSocketHandler extends TextWebSocketHandler {
    private final Map<Long, Set<WebSocketSession>> modelSessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Pattern MODEL_ID_PATTERN = Pattern.compile(".*/ws/generation/(\\d+)/?.*");

    private Long extractModelId(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri != null) {
            Matcher m = MODEL_ID_PATTERN.matcher(uri.getPath());
            if (m.matches()) {
                try {
                    return Long.parseLong(m.group(1));
                } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        Long modelId = extractModelId(session);
        if (modelId != null) {
            modelSessions.computeIfAbsent(modelId, k -> Collections.synchronizedSet(new HashSet<>())).add(session);
            System.out.println("[WebSocket] ModelGeneration client connected for Model " + modelId + ": " + session.getId());
        } else {
            System.out.println("[WebSocket] ModelGeneration client connected (unknown model id): " + session.getId());
        }
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        Long modelId = extractModelId(session);
        if (modelId != null) {
            Set<WebSocketSession> sessions = modelSessions.get(modelId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    modelSessions.remove(modelId);
                }
            }
            System.out.println("[WebSocket] ModelGeneration client disconnected for Model " + modelId + ": " + session.getId());
        }
    }

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) {
        try {
            session.sendMessage(new TextMessage("{\"type\":\"PONG\"}"));
        } catch (IOException ignored) {}
    }

    public void sendToModel(Long modelId, Map<String, Object> payload) {
        Set<WebSocketSession> sessions = modelSessions.get(modelId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(payload);
            TextMessage message = new TextMessage(json);
            synchronized (sessions) {
                for (WebSocketSession session : sessions) {
                    if (session.isOpen()) {
                        try {
                            session.sendMessage(message);
                        } catch (IOException e) {
                            System.err.println("[WebSocket] Error sending to session " + session.getId() + ": " + e.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[WebSocket] Serialization error: " + e.getMessage());
        }
    }
}
