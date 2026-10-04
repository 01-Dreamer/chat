package top.zxylearn.chatserver.websocket;

import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LocalWebSocketRegistry {

    private final Map<Long, Map<String, WebSocketSession>> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public LocalWebSocketRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void add(long userId, String deviceId, WebSocketSession session) {
        WebSocketSession previous = sessions
                .computeIfAbsent(userId, ignored -> new ConcurrentHashMap<>())
                .put(deviceId, session);
        if (previous != null && previous.isOpen() && previous != session) {
            try {
                previous.close();
            } catch (IOException ignored) {
            }
        }
    }

    public void remove(long userId, String deviceId, WebSocketSession session) {
        Map<String, WebSocketSession> userSessions = sessions.get(userId);
        if (userSessions == null) return;
        userSessions.remove(deviceId, session);
        if (userSessions.isEmpty()) sessions.remove(userId, userSessions);
    }

    public void send(long userId, Object payload) {
        Map<String, WebSocketSession> userSessions = sessions.get(userId);
        if (userSessions == null || userSessions.isEmpty()) return;
        final String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception exception) {
            return;
        }
        userSessions.forEach((deviceId, session) -> {
            if (!session.isOpen()) {
                remove(userId, deviceId, session);
                return;
            }
            try {
                synchronized (session) {
                    session.sendMessage(new TextMessage(json));
                }
            } catch (Exception exception) {
                remove(userId, deviceId, session);
                try {
                    session.close();
                } catch (IOException ignored) {
                }
            }
        });
    }

    public void forceLogout(long userId, String currentToken, String message) {
        Map<String, WebSocketSession> userSessions = sessions.get(userId);
        if (userSessions == null || userSessions.isEmpty()) return;
        String json;
        try {
            json = objectMapper.writeValueAsString(Map.of(
                    "type", "FORCED_LOGOUT",
                    "data", Map.of("message", message)));
        } catch (Exception exception) {
            return;
        }
        userSessions.forEach((deviceId, session) -> {
            if (currentToken.equals(String.valueOf(session.getAttributes().get("token")))) return;
            session.getAttributes().put("forcedLogout", true);
            try {
                synchronized (session) {
                    if (session.isOpen()) session.sendMessage(new TextMessage(json));
                }
            } catch (Exception ignored) {
            } finally {
                remove(userId, deviceId, session);
            }
        });
    }
}
