package top.zxylearn.chatserver.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import top.zxylearn.chatserver.exception.BusinessException;

import java.util.List;

@Service
public class LoginRateLimiter {

    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final int maxAttempts;
    private final int windowSeconds;

    public LoginRateLimiter(
            StringRedisTemplate redisTemplate,
            @Value("${chat.rate-limit.login.max-attempts}") int maxAttempts,
            @Value("${chat.rate-limit.login.window-seconds}") int windowSeconds) {
        this.redisTemplate = redisTemplate;
        this.maxAttempts = maxAttempts;
        this.windowSeconds = windowSeconds;
    }

    public void checkAndRecord(String clientAddress, String username) {
        String key = key(clientAddress, username);
        Long attempts = redisTemplate.execute(
                INCREMENT_SCRIPT,
                List.of(key),
                Integer.toString(windowSeconds));
        if (attempts != null && attempts > maxAttempts) {
            throw new BusinessException(
                    "LOGIN_RATE_LIMITED",
                    "登录尝试过于频繁，请稍后再试",
                    HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    public void clear(String clientAddress, String username) {
        redisTemplate.delete(key(clientAddress, username));
    }

    private String key(String clientAddress, String username) {
        String safeAddress = clientAddress == null ? "unknown" : clientAddress.replace(':', '_');
        return "chat:rate:login:" + safeAddress + ":" + username.toLowerCase();
    }
}
