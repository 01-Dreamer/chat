package top.zxylearn.chatserver.mq;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RealtimeEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final String exchangeName;

    public RealtimeEventPublisher(
            RabbitTemplate rabbitTemplate,
            @Value("${chat.mq.realtime-exchange}") String exchangeName) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchangeName = exchangeName;
    }

    public void publish(RealtimeEvent event) {
        rabbitTemplate.convertAndSend(exchangeName, "", event);
    }
}
