package top.zxylearn.chatserver.mq;

import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import top.zxylearn.chatserver.dto.message.SendMessageCommand;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class MessageCommandPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final String queueName;

    public MessageCommandPublisher(
            RabbitTemplate rabbitTemplate,
            @Value("${chat.mq.message-queue}") String queueName) {
        this.rabbitTemplate = rabbitTemplate;
        this.queueName = queueName;
        this.rabbitTemplate.setMandatory(true);
    }

    public void publish(SendMessageCommand command) throws Exception {
        CorrelationData correlation = new CorrelationData(UUID.randomUUID().toString());
        rabbitTemplate.convertAndSend("", queueName, command, correlation);
        CorrelationData.Confirm confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
        if (!confirm.isAck()) throw new IllegalStateException("消息队列未确认请求");
    }
}
