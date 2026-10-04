package top.zxylearn.chatserver.config;

import tools.jackson.databind.json.JsonMapper;
import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    @Bean
    public Queue messagePersistenceQueue(@Value("${chat.mq.message-queue}") String queueName) {
        return new Queue(queueName, true, false, false);
    }

    @Bean
    public Queue failedMessageQueue(@Value("${chat.mq.message-queue}") String queueName) {
        return new Queue(queueName + ".failed", true, false, false);
    }

    @Bean
    public FanoutExchange realtimeExchange(@Value("${chat.mq.realtime-exchange}") String exchangeName) {
        return new FanoutExchange(exchangeName, true, false);
    }

    @Bean
    public AnonymousQueue realtimeQueue() {
        return new AnonymousQueue();
    }

    @Bean
    public Binding realtimeBinding(AnonymousQueue realtimeQueue, FanoutExchange realtimeExchange) {
        return BindingBuilder.bind(realtimeQueue).to(realtimeExchange);
    }

    @Bean
    public MessageConverter rabbitMessageConverter(JsonMapper objectMapper) {
        return new JacksonJsonMessageConverter(objectMapper);
    }
}
