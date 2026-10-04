package top.zxylearn.chatserver.mq;

import jakarta.annotation.PreDestroy;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class RealtimeEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final String exchangeName;
    private final ExecutorService publishExecutor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "realtime-event-publisher");
        thread.setDaemon(true);
        return thread;
    });

    public RealtimeEventPublisher(
            RabbitTemplate rabbitTemplate,
            @Value("${chat.mq.realtime-exchange}") String exchangeName) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchangeName = exchangeName;
    }

    public void publish(RealtimeEvent event) {
        publishExecutor.execute(() -> {
            try {
                rabbitTemplate.convertAndSend(exchangeName, "", event);
            } catch (Exception ignored) {
                // Durable message/contact events remain recoverable from user_inbox.
                // Ephemeral previews are retried by the originating client command.
            }
        });
    }

    @PreDestroy
    public void close() {
        publishExecutor.shutdownNow();
    }
}
