package top.zxylearn.chatserver.service;

import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.zxylearn.chatserver.vo.VersionedSyncResponse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Supplier;

@Service
public class DirectorySnapshotCache {

    private static final Duration TTL = Duration.ofMinutes(5);
    private static final String PREFIX = "directory:snapshot:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public DirectorySnapshotCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public <T> VersionedSyncResponse<T> sync(
            String key,
            String clientVersion,
            Class<T> itemType,
            Supplier<List<T>> loader) {
        List<T> items = read(key, itemType);
        if (items == null) {
            items = loader.get();
            write(key, items);
        }
        String json = toJson(items);
        String version = hash(json);
        boolean changed = clientVersion == null || !version.equals(clientVersion);
        return new VersionedSyncResponse<>(version, changed, changed ? items : List.of());
    }

    public void evict(String... keys) {
        for (String key : keys) evictNowAndAfterCommit(key);
    }

    public String friendsKey(long userId) { return PREFIX + "friends:" + userId; }
    public String friendRequestsKey(long userId) { return PREFIX + "friend-requests:" + userId; }
    public String groupsKey(long userId) { return PREFIX + "groups:" + userId; }
    public String groupRequestsKey(long userId) { return PREFIX + "group-requests:" + userId; }
    public String groupMembersKey(long groupId) { return PREFIX + "group-members:" + groupId; }

    private <T> List<T> read(String key, Class<T> itemType) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) return null;
            JavaType type = objectMapper.getTypeFactory().constructCollectionType(List.class, itemType);
            return objectMapper.readValue(json, type);
        } catch (Exception ignored) {
            try { redisTemplate.delete(key); } catch (Exception ignoredAgain) { }
            return null;
        }
    }

    private void write(String key, List<?> items) {
        try {
            redisTemplate.opsForValue().set(key, toJson(items), TTL);
        } catch (Exception ignored) {
            // Redis is an optimization; MySQL remains the source of truth.
        }
    }

    private String toJson(List<?> items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (Exception exception) {
            throw new IllegalStateException("无法序列化通讯录缓存", exception);
        }
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 12);
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成同步版本", exception);
        }
    }

    private void evictNowAndAfterCommit(String key) {
        deleteQuietly(key);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteQuietly(key);
            }
        });
    }

    private void deleteQuietly(String key) {
        try { redisTemplate.delete(key); } catch (Exception ignored) { }
    }
}
