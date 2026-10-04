package top.zxylearn.chatserver.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ChatAccessCache {

    private static final Duration ACCESS_TTL = Duration.ofMinutes(10);
    private static final String DIRECT_PREFIX = "chat:access:direct:";
    private static final String GROUP_PREFIX = "chat:access:group:";

    private final StringRedisTemplate redisTemplate;

    public ChatAccessCache(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Optional<Boolean> getDirectAccess(long firstUserId, long secondUserId) {
        String value = redisTemplate.opsForValue().get(directKey(firstUserId, secondUserId));
        return value == null ? Optional.empty() : Optional.of("1".equals(value));
    }

    public void cacheDirectAccess(long firstUserId, long secondUserId, boolean allowed) {
        redisTemplate.opsForValue().set(
                directKey(firstUserId, secondUserId), allowed ? "1" : "0", ACCESS_TTL);
    }

    public Optional<List<Long>> getGroupRecipients(long groupId) {
        String value = redisTemplate.opsForValue().get(groupKey(groupId));
        if (value == null) return Optional.empty();
        if (value.isBlank()) return Optional.of(List.of());
        return Optional.of(Arrays.stream(value.split(","))
                .map(Long::parseLong)
                .toList());
    }

    public void cacheGroupRecipients(long groupId, List<Long> recipientIds) {
        String value = recipientIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        redisTemplate.opsForValue().set(groupKey(groupId), value, ACCESS_TTL);
    }

    public void evictDirect(long firstUserId, long secondUserId) {
        evictNowAndAfterCommit(directKey(firstUserId, secondUserId));
    }

    public void evictGroup(long groupId) {
        evictNowAndAfterCommit(groupKey(groupId));
    }

    private void evictNowAndAfterCommit(String key) {
        redisTemplate.delete(key);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                redisTemplate.delete(key);
            }
        });
    }

    private String directKey(long firstUserId, long secondUserId) {
        return DIRECT_PREFIX + Math.min(firstUserId, secondUserId) + ":" + Math.max(firstUserId, secondUserId);
    }

    private String groupKey(long groupId) {
        return GROUP_PREFIX + groupId + ":recipients";
    }
}
