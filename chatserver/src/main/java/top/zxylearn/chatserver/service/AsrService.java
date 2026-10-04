package top.zxylearn.chatserver.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import top.zxylearn.chatserver.entity.FileResource;
import top.zxylearn.chatserver.exception.BusinessException;
import top.zxylearn.chatserver.mapper.FileResourceMapper;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class AsrService {

    private static final Logger log = LoggerFactory.getLogger(AsrService.class);

    private final FileResourceMapper fileMapper;
    private final OssFileService fileService;
    private final StringRedisTemplate redisTemplate;
    private final ActionRateLimiter rateLimiter;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
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
            ObjectMapper objectMapper,
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
        this.objectMapper = objectMapper;
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
        JsonNode submitted = submit(audioUrl);
        String taskId = textAt(submitted, "/output/task_id");
        if (taskId == null) throw asrFailure();
        String transcriptionUrl = waitForResult(taskId);
        JsonNode result = getJson(RestClient.create(), URI.create(transcriptionUrl), "下载语音识别结果失败");
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
            JsonNode response = getJson(restClient, "/api/v1/tasks/" + taskId, "查询语音识别任务失败");
            String status = textAt(response, "/output/task_status");
            if ("SUCCEEDED".equals(status)) {
                JsonNode results = response.at("/output/results");
                if (results.isArray() && !results.isEmpty()
                        && "SUCCEEDED".equals(textAt(results.get(0), "/subtask_status"))) {
                    String url = textAt(results.get(0), "/transcription_url");
                    if (url != null) return url;
                }
                String url = textAt(response, "/output/transcription_url");
                if (url != null) return url;
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

    private JsonNode submit(String audioUrl) {
        try {
            String response = restClient.post()
                    .uri("/api/v1/services/audio/asr/transcription")
                    .header("X-DashScope-Async", "enable")
                    .body(Map.of(
                            "model", model,
                            "input", Map.of("file_urls", List.of(audioUrl)),
                            "parameters", Map.of("channel_id", List.of(0))))
                    .retrieve()
                    .body(String.class);
            return parseJson(response, "提交语音识别任务失败");
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            log.warn("ASR submit failed with status {}: {}", exception.getStatusCode(), safeBody(exception));
            throw remoteFailure("提交语音识别任务失败");
        } catch (RestClientException exception) {
            log.warn("ASR submit request failed", exception);
            throw remoteFailure("无法连接语音识别服务");
        }
    }

    private JsonNode getJson(RestClient client, String uri, String failureMessage) {
        try {
            String response = client.get().uri(uri).retrieve().body(String.class);
            return parseJson(response, failureMessage);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            log.warn("ASR request failed with status {}: {}", exception.getStatusCode(), safeBody(exception));
            throw remoteFailure(failureMessage);
        } catch (RestClientException exception) {
            log.warn("ASR request failed", exception);
            throw remoteFailure(failureMessage);
        }
    }

    private JsonNode getJson(RestClient client, URI uri, String failureMessage) {
        try {
            String response = client.get().uri(uri).retrieve().body(String.class);
            return parseJson(response, failureMessage);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            log.warn("ASR request failed with status {}: {}", exception.getStatusCode(), safeBody(exception));
            throw remoteFailure(failureMessage);
        } catch (RestClientException exception) {
            log.warn("ASR request failed", exception);
            throw remoteFailure(failureMessage);
        }
    }

    private JsonNode parseJson(String value, String failureMessage) {
        if (value == null || value.isBlank()) throw remoteFailure(failureMessage);
        try {
            return objectMapper.readTree(value);
        } catch (Exception exception) {
            log.warn("ASR returned invalid JSON", exception);
            throw remoteFailure(failureMessage);
        }
    }

    private String safeBody(RestClientResponseException exception) {
        String body = exception.getResponseBodyAsString();
        return body.length() > 1_000 ? body.substring(0, 1_000) : body;
    }

    private String textAt(JsonNode node, String pointer) {
        if (node == null) return null;
        JsonNode value = node.at(pointer);
        return value.isMissingNode() || value.isNull() ? null : value.asString();
    }

    private BusinessException asrFailure() {
        return new BusinessException("ASR_FAILED", "语音识别失败，请稍后重试", HttpStatus.BAD_GATEWAY);
    }

    private BusinessException remoteFailure(String message) {
        return new BusinessException("ASR_SERVICE_ERROR", message + "，请稍后重试", HttpStatus.BAD_GATEWAY);
    }
}
