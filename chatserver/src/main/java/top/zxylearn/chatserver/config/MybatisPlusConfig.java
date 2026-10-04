package top.zxylearn.chatserver.config;

import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusConfig {

    private static final long MAX_NODE_ID = 31L;

    @Bean
    public IdentifierGenerator identifierGenerator(
            @Value("${chat.snowflake.worker-id}") long workerId,
            @Value("${chat.snowflake.datacenter-id}") long datacenterId) {
        validateNodeId("worker-id", workerId);
        validateNodeId("datacenter-id", datacenterId);
        return new DefaultIdentifierGenerator(workerId, datacenterId);
    }

    private void validateNodeId(String name, long value) {
        if (value < 0 || value > MAX_NODE_ID) {
            throw new IllegalArgumentException(
                    "chat.snowflake." + name + " must be between 0 and " + MAX_NODE_ID);
        }
    }
}
