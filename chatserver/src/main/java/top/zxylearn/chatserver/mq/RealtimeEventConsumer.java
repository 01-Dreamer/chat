package top.zxylearn.chatserver.mq;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import top.zxylearn.chatserver.websocket.LocalWebSocketRegistry;

@Component
public class RealtimeEventConsumer {

    private final LocalWebSocketRegistry registry;

    public RealtimeEventConsumer(LocalWebSocketRegistry registry) {
        this.registry = registry;
    }

    @RabbitListener(queues = "#{realtimeQueue.name}")
    public void receive(RealtimeEvent event) {
        event.targetSequences().forEach((userId, sequence) -> registry.send(
                userId,
                new UserRealtimeEvent(
                        event.type(),
                        sequence > 0 ? sequence : null,
                        event.data(),
                        event.errorCode(),
                        event.errorMessage(),
                        event.clientMessageId())));
    }

    private record UserRealtimeEvent(
            String type,
            Long sequence,
            Object data,
            String errorCode,
            String errorMessage,
            String clientMessageId) {
    }
}
