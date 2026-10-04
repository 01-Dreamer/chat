package top.zxylearn.chatserver.mq;

import com.rabbitmq.client.Channel;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import top.zxylearn.chatserver.dto.message.SendMessageCommand;
import top.zxylearn.chatserver.exception.BusinessException;
import top.zxylearn.chatserver.service.MessagePersistenceService;

import java.io.IOException;

@Component
public class MessageCommandConsumer {

    private final MessagePersistenceService persistenceService;
    private final RealtimeEventPublisher realtimePublisher;
    private final RabbitTemplate rabbitTemplate;
    private final String failedQueue;

    public MessageCommandConsumer(
            MessagePersistenceService persistenceService,
            RealtimeEventPublisher realtimePublisher,
            RabbitTemplate rabbitTemplate,
            @Value("${chat.mq.message-queue}") String messageQueue) {
        this.persistenceService = persistenceService;
        this.realtimePublisher = realtimePublisher;
        this.rabbitTemplate = rabbitTemplate;
        this.failedQueue = messageQueue + ".failed";
    }

    @RabbitListener(queues = "${chat.mq.message-queue}")
    public void consume(
            SendMessageCommand command,
            org.springframework.amqp.core.Message amqpMessage,
            Channel channel) throws IOException {
        long deliveryTag = amqpMessage.getMessageProperties().getDeliveryTag();
        try {
            MessagePersistenceService.PersistedMessage persisted = persistenceService.persist(command);
            realtimePublisher.publish(RealtimeEvent.businessEvent(
                    persisted.duplicate() ? "MESSAGE_ACK" : "MESSAGE",
                    persisted.targetSequences(),
                    persisted.message()));
            channel.basicAck(deliveryTag, false);
        } catch (BusinessException exception) {
            realtimePublisher.publish(RealtimeEvent.sendFailed(
                    command.senderId(), command.clientMessageId(), exception.getCode(), exception.getMessage()));
            channel.basicAck(deliveryTag, false);
        } catch (Exception exception) {
            if (Boolean.TRUE.equals(amqpMessage.getMessageProperties().getRedelivered())) {
                rabbitTemplate.convertAndSend(failedQueue, command);
                realtimePublisher.publish(RealtimeEvent.sendFailed(
                        command.senderId(), command.clientMessageId(), "MESSAGE_PROCESSING_FAILED", "消息处理失败，请稍后重试"));
                channel.basicAck(deliveryTag, false);
            } else {
                channel.basicNack(deliveryTag, false, true);
            }
        }
    }
}
