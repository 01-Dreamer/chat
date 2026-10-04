package top.zxylearn.chatserver.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import top.zxylearn.chatserver.entity.FileResource;
import top.zxylearn.chatserver.exception.BusinessException;
import top.zxylearn.chatserver.mapper.FileResourceMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class AsrService {

    private final FileResourceMapper fileMapper;
    private final OssFileService fileService;
    private final StringRedisTemplate redisTemplate;
    private final ActionRateLimiter rateLimiter;
    private final RestClient restClient;
    private final String model;
    private final int maxRequests;
    private final int windowSeconds;
    private final Duration cacheTtl;

    public AsrService(
            FileResourceMapper fileMapper,
            OssFileService fileService,
            StringRedisTemplate redisTemplate,
            ActionRateLimiter rateLimiter,
            RestClient.Builder restClientBuilder,
            @Value("${aliyun.asr.base-url}") String baseUrl,
            @Value("${aliyun.asr.api-key}") String apiKey,
            @Value("${aliyun.asr.model}") String model,
            @Value("${chat.rate-limit.asr.max-requests}") int maxRequests,
            @Value("${chat.rate-limit.asr.window-seconds}") int windowSeconds,
            @Value("${chat.cache.asr-hours}") long cacheHours) {
        this.fileMapper = fileMapper;
        this.fileService = fileService;
        this.redisTemplate = redisTemplate;
        this.rateLimiter = rateLimiter;
        this.restClient = restClientBuilder.baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
        this.model = model;
        this.maxRequests = maxRequests;
        this.windowSeconds = windowSeconds;
        this.cacheTtl = Duration.ofHours(cacheHours);
    }

    public String transcribe(long userId, long resourceId) {
        FileResource resource = fileMapper.selectById(resourceId);
        if (resource == null) throw new BusinessException("FILE_NOT_FOUND", "语音资源不存在", HttpStatus.NOT_FOUND);
        if (resource.getResourceType() != 2) {
            throw new BusinessException("ASR_AUDIO_REQUIRED", "只有语音资源可以转文字", HttpStatus.BAD_REQUEST);
        }
        fileService.assertCanAccess(userId, resource);
        String cacheKey = "chat:asr:" + resource.getFileHash();
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) return cached;
        rateLimiter.check("asr", userId, maxRequests, windowSeconds);
        String audioUrl = fileService.signedDownloadUrl(userId, resourceId);
        JsonNode submitted = restClient.post()
                .uri("/api/v1/services/audio/asr/transcription")
                .header("X-DashScope-Async", "enable")
                .body(Map.of(
                        "model", model,
                        "input", Map.of("file_urls", List.of(audioUrl)),
                        "parameters", Map.of("channel_id", List.of(0))))
                .retrieve()
                .body(JsonNode.class);
        String taskId = textAt(submitted, "/output/task_id");
        if (taskId == null) throw asrFailure();
        String transcriptionUrl = waitForResult(taskId);
        JsonNode result = RestClient.create().get().uri(transcriptionUrl).retrieve().body(JsonNode.class);
        JsonNode transcripts = result == null ? null : result.get("transcripts");
        if (transcripts == null || !transcripts.isArray() || transcripts.isEmpty()) throw asrFailure();
        StringBuilder text = new StringBuilder();
        for (JsonNode transcript : transcripts) {
            JsonNode content = transcript.get("text");
            if (content != null && !content.asString().isBlank()) {
                if (!text.isEmpty()) text.append('\n');
                text.append(content.asString().trim());
            }
        }
        if (text.isEmpty()) throw asrFailure();
        String recognized = text.toString();
        redisTemplate.opsForValue().set(cacheKey, recognized, cacheTtl);
        return recognized;
    }

    private String waitForResult(String taskId) {
        for (int attempt = 0; attempt < 40; attempt++) {
            JsonNode response = restClient.get()
                    .uri("/api/v1/tasks/{taskId}", taskId)
                    .retrieve()
                    .body(JsonNode.class);
            String status = textAt(response, "/output/task_status");
            if ("SUCCEEDED".equals(status)) {
                JsonNode results = response.at("/output/results");
                if (results.isArray() && !results.isEmpty()
                        && "SUCCEEDED".equals(textAt(results.get(0), "/subtask_status"))) {
                    String url = textAt(results.get(0), "/transcription_url");
                    if (url != null) return url;
                }
                throw asrFailure();
            }
            if ("FAILED".equals(status) || "CANCELED".equals(status)) throw asrFailure();
            try {
                Thread.sleep(1_500);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw asrFailure();
            }
        }
        throw new BusinessException("ASR_TIMEOUT", "语音识别超时，请稍后重试", HttpStatus.GATEWAY_TIMEOUT);
    }

    private String textAt(JsonNode node, String pointer) {
        if (node == null) return null;
        JsonNode value = node.at(pointer);
        return value.isMissingNode() || value.isNull() ? null : value.asString();
    }

    private BusinessException asrFailure() {
        return new BusinessException("ASR_FAILED", "语音识别失败，请稍后重试", HttpStatus.BAD_GATEWAY);
    }
}
