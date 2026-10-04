package top.zxylearn.chatserver.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import top.zxylearn.chatserver.exception.BusinessException;

import java.util.List;

@Service
public class ActionRateLimiter {

    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
            return current
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public ActionRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void check(String action, long userId, int limit, int windowSeconds) {
        Long count = redisTemplate.execute(
                SCRIPT,
                List.of("chat:rate:" + action + ":" + userId),
                Integer.toString(windowSeconds));
        if (count != null && count > limit) {
            throw new BusinessException(
                    "RATE_LIMITED", "请求过于频繁，请稍后重试", HttpStatus.TOO_MANY_REQUESTS);
        }
    }
}
