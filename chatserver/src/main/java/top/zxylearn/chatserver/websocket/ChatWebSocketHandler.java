package top.zxylearn.chatserver.websocket;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import top.zxylearn.chatserver.dto.message.SendMessageCommand;
import top.zxylearn.chatserver.mq.MessageCommandPublisher;
import top.zxylearn.chatserver.service.CallManagementService;
import top.zxylearn.chatserver.service.ActionRateLimiter;
import top.zxylearn.chatserver.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;

import java.util.Map;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final LocalWebSocketRegistry registry;
    private final MessageCommandPublisher messagePublisher;
    private final CallManagementService callService;
    private final ActionRateLimiter rateLimiter;
    private final int messageRateLimit;
    private final int messageRateWindow;

    public ChatWebSocketHandler(
            ObjectMapper objectMapper,
            LocalWebSocketRegistry registry,
            MessageCommandPublisher messagePublisher,
            CallManagementService callService,
            ActionRateLimiter rateLimiter,
            @Value("${chat.rate-limit.message.max-requests}") int messageRateLimit,
            @Value("${chat.rate-limit.message.window-seconds}") int messageRateWindow) {
        this.objectMapper = objectMapper;
        this.registry = registry;
        this.messagePublisher = messagePublisher;
        this.callService = callService;
        this.rateLimiter = rateLimiter;
        this.messageRateLimit = messageRateLimit;
        this.messageRateWindow = messageRateWindow;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        registry.add(userId(session), deviceId(session), session);
        registry.send(userId(session), Map.of("type", "CONNECTED"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        if (Boolean.TRUE.equals(session.getAttributes().get("forcedLogout"))) return;
        IncomingMessage incoming;
        try {
            incoming = objectMapper.readValue(message.getPayload(), IncomingMessage.class);
        } catch (Exception exception) {
            sendError(session, null, "INVALID_FRAME", "WebSocket 消息格式不正确");
            return;
        }
        if ("PING".equals(incoming.type())) {
            synchronized (session) {
                session.sendMessage(new TextMessage("{\"type\":\"PONG\"}"));
            }
            return;
        }
        if ("SIGNAL".equals(incoming.type()) && incoming.data() != null) {
            try {
                ClientSignal signal = objectMapper.treeToValue(incoming.data(), ClientSignal.class);
                if (signal.signalType() == null || !signal.signalType().matches("offer|answer|ice|hangup")) {
                    throw new IllegalArgumentException();
                }
                callService.relaySignal(
                        userId(session),
                        Long.parseLong(signal.callId()),
                        Long.parseLong(signal.targetUserId()),
                        signal.signalType(),
                        signal.payload());
            } catch (Exception exception) {
                sendError(session, null, "INVALID_SIGNAL", "音视频通话信令无效");
            }
            return;
        }
        if (!"SEND_MESSAGE".equals(incoming.type()) || incoming.data() == null) {
            sendError(session, null, "UNSUPPORTED_FRAME", "不支持的 WebSocket 消息类型");
            return;
        }
        ClientSendMessage input;
        try {
            input = objectMapper.treeToValue(incoming.data(), ClientSendMessage.class);
            rateLimiter.check("message", userId(session), messageRateLimit, messageRateWindow);
            SendMessageCommand command = new SendMessageCommand(
                    userId(session),
                    input.clientMessageId(),
                    input.chatType(),
                    input.targetId(),
                    input.messageType(),
                    input.content(),
                    input.referenceId(),
                    input.replyMessageId());
            messagePublisher.publish(command);
        } catch (BusinessException exception) {
            sendError(session, inputClientMessageId(incoming.data()), exception.getCode(), exception.getMessage());
        } catch (Exception exception) {
            String clientMessageId = inputClientMessageId(incoming.data());
            sendError(session, clientMessageId, "MESSAGE_QUEUE_UNAVAILABLE", "消息暂时无法发送，请稍后重试");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        registry.remove(userId(session), deviceId(session), session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        registry.remove(userId(session), deviceId(session), session);
        try {
            session.close(CloseStatus.SERVER_ERROR);
        } catch (Exception ignored) {
        }
    }

    private void sendError(WebSocketSession session, String clientMessageId, String code, String message) {
        registry.send(userId(session), new OutgoingError("SEND_FAILED", code, message, clientMessageId));
    }

    private long userId(WebSocketSession session) {
        return (long) session.getAttributes().get("userId");
    }

    private String deviceId(WebSocketSession session) {
        return String.valueOf(session.getAttributes().get("deviceId"));
    }

    private String inputClientMessageId(JsonNode data) {
        JsonNode value = data.get("clientMessageId");
        return value == null || value.isNull() ? null : value.asText();
    }

    private record IncomingMessage(String type, JsonNode data) {
    }

    private record ClientSendMessage(
            String clientMessageId,
            int chatType,
            String targetId,
            int messageType,
            String content,
            String referenceId,
            String replyMessageId) {
    }

    private record ClientSignal(
            String callId,
            String targetUserId,
            String signalType,
            JsonNode payload) {
    }

    private record OutgoingError(
            String type,
            String errorCode,
            String errorMessage,
            String clientMessageId) {
    }
}
