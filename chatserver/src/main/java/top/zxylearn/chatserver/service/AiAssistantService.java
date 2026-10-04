package top.zxylearn.chatserver.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import top.zxylearn.chatserver.dto.ai.SmartReplyRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@Service
public class AiAssistantService {

    private final ChatClient chatClient;
    private final StringRedisTemplate redisTemplate;
    private final ActionRateLimiter rateLimiter;
    private final int maxRequests;
    private final int windowSeconds;
    private final Duration translationTtl;

    public AiAssistantService(
            ChatClient.Builder chatClientBuilder,
            StringRedisTemplate redisTemplate,
            ActionRateLimiter rateLimiter,
            @Value("${chat.rate-limit.ai.max-requests}") int maxRequests,
            @Value("${chat.rate-limit.ai.window-seconds}") int windowSeconds,
            @Value("${chat.cache.translation-hours}") long cacheHours) {
        this.chatClient = chatClientBuilder.build();
        this.redisTemplate = redisTemplate;
        this.rateLimiter = rateLimiter;
        this.maxRequests = maxRequests;
        this.windowSeconds = windowSeconds;
        this.translationTtl = Duration.ofHours(cacheHours);
    }

    public String translate(long userId, String text, String targetLanguage) {
        String normalizedLanguage = targetLanguage.trim().toLowerCase();
        String cacheKey = "chat:translation:" + sha256(text) + ":" + normalizedLanguage;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) return cached;
        rateLimiter.check("ai", userId, maxRequests, windowSeconds);
        String result = chatClient.prompt()
                .system("You are a translation engine. Translate only the text supplied by the user. Return only the translated text, without explanations. Target language: " + normalizedLanguage)
                .user(text)
                .call()
                .content();
        if (result == null || result.isBlank()) throw new IllegalStateException("翻译服务未返回结果");
        String normalized = result.trim();
        redisTemplate.opsForValue().set(cacheKey, normalized, translationTtl);
        return normalized;
    }

    public List<String> smartReplies(long userId, List<SmartReplyRequest.ContextMessage> messages) {
        rateLimiter.check("ai", userId, maxRequests, windowSeconds);
        StringBuilder context = new StringBuilder();
        int start = Math.max(0, messages.size() - 12);
        for (int index = start; index < messages.size(); index++) {
            SmartReplyRequest.ContextMessage message = messages.get(index);
            context.append(message.role()).append(": ").append(message.content()).append('\n');
        }
        String result = chatClient.prompt()
                .system("根据聊天上下文给出3条简短、自然、可直接发送的中文回复建议。每条单独一行，不要编号，不要解释。")
                .user(context.toString())
                .call()
                .content();
        if (result == null || result.isBlank()) return List.of();
        return Arrays.stream(result.split("\\R"))
                .map(line -> line.replaceFirst("^[\\s\\-•\\d.、]+", "").trim())
                .filter(line -> !line.isBlank())
                .limit(3)
                .toList();
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("无法计算文本哈希", exception);
        }
    }
}
