package top.zxylearn.chatserver.mq;

import jakarta.annotation.PreDestroy;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import top.zxylearn.chatserver.dto.message.SendMessageCommand;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
public class MessageCommandPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final String queueName;
    private final ExecutorService publishExecutor = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "message-command-publisher");
        thread.setDaemon(true);
        return thread;
    });

    public MessageCommandPublisher(
            RabbitTemplate rabbitTemplate,
            @Value("${chat.mq.message-queue}") String queueName) {
        this.rabbitTemplate = rabbitTemplate;
        this.queueName = queueName;
        this.rabbitTemplate.setMandatory(true);
    }

    public CompletableFuture<Void> publish(SendMessageCommand command) {
        return CompletableFuture.runAsync(() -> {
            try {
                CorrelationData correlation = new CorrelationData(UUID.randomUUID().toString());
                rabbitTemplate.convertAndSend("", queueName, command, correlation);
                CorrelationData.Confirm confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
                    if (!confirm.isAck()) throw new IllegalStateException("消息队列未确认请求");
            } catch (Exception exception) {
                throw new IllegalStateException("消息队列暂时不可用", exception);
            }
        }, publishExecutor);
    }

    @PreDestroy
    public void close() {
        publishExecutor.shutdownNow();
    }
}
