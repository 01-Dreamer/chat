package top.zxylearn.chatserver.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.AbortMultipartUploadRequest;
import com.aliyun.oss.model.CompleteMultipartUploadRequest;
import com.aliyun.oss.model.InitiateMultipartUploadRequest;
import com.aliyun.oss.model.InitiateMultipartUploadResult;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.PartETag;
import com.aliyun.oss.model.UploadPartRequest;
import com.aliyun.oss.model.UploadPartResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import top.zxylearn.chatserver.dto.file.InitializeUploadRequest;
import top.zxylearn.chatserver.entity.FileResource;
import top.zxylearn.chatserver.exception.BusinessException;
import top.zxylearn.chatserver.mapper.FileResourceMapper;
import top.zxylearn.chatserver.mapper.FileResourceAccessMapper;
import top.zxylearn.chatserver.entity.FileResourceAccess;
import top.zxylearn.chatserver.mapper.GroupMemberMapper;
import top.zxylearn.chatserver.mapper.MessageMapper;
import top.zxylearn.chatserver.entity.GroupMember;
import top.zxylearn.chatserver.entity.Message;
import top.zxylearn.chatserver.vo.FileResourceResponse;
import top.zxylearn.chatserver.vo.UploadSessionResponse;

import java.io.InputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OssFileService {

    private static final Duration SESSION_TTL = Duration.ofHours(24);
    private final OSS ossClient;
    private final StringRedisTemplate redisTemplate;
    private final FileResourceMapper fileMapper;
    private final FileResourceAccessMapper accessMapper;
    private final MessageMapper messageMapper;
    private final GroupMemberMapper memberMapper;
    private final String bucketName;
    private final String endpoint;
    private final long maxFileSize;
    private final long partSize;

    public OssFileService(
            OSS ossClient,
            StringRedisTemplate redisTemplate,
            FileResourceMapper fileMapper,
            FileResourceAccessMapper accessMapper,
            MessageMapper messageMapper,
            GroupMemberMapper memberMapper,
            @Value("${aliyun.oss.bucketName}") String bucketName,
            @Value("${aliyun.oss.endpoint}") String endpoint,
            @Value("${chat.file.max-size}") long maxFileSize,
            @Value("${chat.file.part-size}") long partSize) {
        this.ossClient = ossClient;
        this.redisTemplate = redisTemplate;
        this.fileMapper = fileMapper;
        this.accessMapper = accessMapper;
        this.messageMapper = messageMapper;
        this.memberMapper = memberMapper;
        this.bucketName = bucketName;
        this.endpoint = endpoint;
        this.maxFileSize = maxFileSize;
        this.partSize = partSize;
    }

    @Transactional
    public UploadSessionResponse initialize(long userId, InitializeUploadRequest request) {
        if (request.fileSize() > maxFileSize) {
            throw new BusinessException("FILE_TOO_LARGE", "文件大小超过限制", HttpStatus.PAYLOAD_TOO_LARGE);
        }
        String hash = request.fileHash().toLowerCase();
        FileResource existing = findByHash(hash);
        if (existing != null) {
            grantAccess(existing.getId(), userId);
            return new UploadSessionResponse(true, null, partSize, List.of(), FileResourceResponse.from(existing));
        }

        String safeName = request.fileName().trim().replaceAll("[^a-zA-Z0-9._\\-\\u4e00-\\u9fa5]", "_");
        String objectKey = "chat/" + hash.substring(0, 2) + "/" + hash + "/" + UUID.randomUUID() + "-" + safeName;
        InitiateMultipartUploadResult initiated = ossClient.initiateMultipartUpload(
                new InitiateMultipartUploadRequest(bucketName, objectKey));
        String sessionId = UUID.randomUUID().toString();
        Map<String, String> state = new HashMap<>();
        state.put("userId", Long.toString(userId));
        state.put("ossUploadId", initiated.getUploadId());
        state.put("objectKey", objectKey);
        state.put("fileName", request.fileName().trim());
        state.put("fileSize", request.fileSize().toString());
        state.put("mimeType", request.mimeType().trim());
        state.put("fileHash", hash);
        state.put("resourceType", request.resourceType().toString());
        if (request.duration() != null) state.put("duration", request.duration().toString());
        String key = uploadKey(sessionId);
        redisTemplate.opsForHash().putAll(key, state);
        redisTemplate.expire(key, SESSION_TTL);
        return new UploadSessionResponse(false, sessionId, partSize, List.of(), null);
    }

    public UploadSessionResponse status(long userId, String sessionId) {
        Map<Object, Object> state = requireSession(userId, sessionId);
        return new UploadSessionResponse(false, sessionId, partSize, uploadedParts(state), null);
    }

    public int uploadPart(long userId, String sessionId, int partNumber, MultipartFile file) {
        if (partNumber < 1 || partNumber > 10_000) {
            throw new BusinessException("INVALID_PART_NUMBER", "分片编号不正确", HttpStatus.BAD_REQUEST);
        }
        if (file.isEmpty() || file.getSize() > partSize) {
            throw new BusinessException("INVALID_FILE_PART", "文件分片为空或超过限制", HttpStatus.BAD_REQUEST);
        }
        Map<Object, Object> state = requireSession(userId, sessionId);
        try (InputStream input = file.getInputStream()) {
            UploadPartRequest request = new UploadPartRequest();
            request.setBucketName(bucketName);
            request.setKey(value(state, "objectKey"));
            request.setUploadId(value(state, "ossUploadId"));
            request.setInputStream(input);
            request.setPartSize(file.getSize());
            request.setPartNumber(partNumber);
            UploadPartResult result = ossClient.uploadPart(request);
            String redisKey = uploadKey(sessionId);
            redisTemplate.opsForHash().put(redisKey, "part:" + partNumber, result.getETag());
            redisTemplate.expire(redisKey, SESSION_TTL);
            return partNumber;
        } catch (Exception exception) {
            throw new BusinessException("FILE_PART_UPLOAD_FAILED", "文件分片上传失败", HttpStatus.BAD_GATEWAY);
        }
    }

    @Transactional
    public FileResourceResponse complete(long userId, String sessionId) {
        Map<Object, Object> state = requireSession(userId, sessionId);
        String hash = value(state, "fileHash");
        String lockKey = "chat:file:complete:" + hash;
        if (!Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(lockKey, sessionId, Duration.ofMinutes(2)))) {
            throw new BusinessException("FILE_COMPLETING", "相同文件正在完成上传，请稍后重试", HttpStatus.CONFLICT);
        }
        try {
            FileResource existing = findByHash(hash);
            if (existing != null) {
                grantAccess(existing.getId(), userId);
                abortOss(state);
                redisTemplate.delete(uploadKey(sessionId));
                return FileResourceResponse.from(existing);
            }
            List<PartETag> tags = partTags(state);
            if (tags.isEmpty()) throw new BusinessException("FILE_PARTS_MISSING", "尚未上传文件分片", HttpStatus.CONFLICT);
            ossClient.completeMultipartUpload(new CompleteMultipartUploadRequest(
                    bucketName, value(state, "objectKey"), value(state, "ossUploadId"), tags));
            if (!hash.equals(calculateRemoteSha256(value(state, "objectKey")))) {
                ossClient.deleteObject(bucketName, value(state, "objectKey"));
                redisTemplate.delete(uploadKey(sessionId));
                throw new BusinessException("FILE_HASH_MISMATCH", "文件完整性校验失败", HttpStatus.CONFLICT);
            }
            LocalDateTime now = LocalDateTime.now();
            FileResource resource = new FileResource();
            resource.setUploaderId(userId);
            resource.setResourceType(Integer.parseInt(value(state, "resourceType")));
            resource.setFileName(value(state, "fileName"));
            resource.setFileSize(Long.parseLong(value(state, "fileSize")));
            resource.setMimeType(value(state, "mimeType"));
            resource.setFileHash(hash);
            resource.setFileUrl(publicUrl(value(state, "objectKey")));
            if (state.containsKey("duration")) resource.setDuration(Integer.parseInt(value(state, "duration")));
            resource.setCreatedTime(now);
            resource.setUpdatedTime(now);
            fileMapper.insert(resource);
            grantAccess(resource.getId(), userId);
            redisTemplate.delete(uploadKey(sessionId));
            return FileResourceResponse.from(resource);
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    public void abort(long userId, String sessionId) {
        Map<Object, Object> state = requireSession(userId, sessionId);
        abortOss(state);
        redisTemplate.delete(uploadKey(sessionId));
    }

    public FileResourceResponse get(long userId, long resourceId) {
        FileResource resource = fileMapper.selectById(resourceId);
        if (resource == null) throw new BusinessException("FILE_NOT_FOUND", "文件资源不存在", HttpStatus.NOT_FOUND);
        assertCanAccess(userId, resource);
        return FileResourceResponse.from(resource);
    }

    public String signedDownloadUrl(long userId, long resourceId) {
        FileResource resource = fileMapper.selectById(resourceId);
        if (resource == null) throw new BusinessException("FILE_NOT_FOUND", "文件资源不存在", HttpStatus.NOT_FOUND);
        assertCanAccess(userId, resource);
        String prefix = "https://" + bucketName + "." + endpoint + "/";
        String objectKey = resource.getFileUrl().startsWith(prefix)
                ? resource.getFileUrl().substring(prefix.length())
                : resource.getFileUrl();
        return ossClient.generatePresignedUrl(
                bucketName, objectKey, new Date(System.currentTimeMillis() + 10 * 60_000L)).toString();
    }

    public void assertCanAccess(long userId, FileResource resource) {
        if (accessMapper.selectCount(new LambdaQueryWrapper<FileResourceAccess>()
                .eq(FileResourceAccess::getResourceId, resource.getId())
                .eq(FileResourceAccess::getUserId, userId)) > 0) return;
        List<Message> messages = messageMapper.selectList(new LambdaQueryWrapper<Message>()
                .eq(Message::getReferenceId, resource.getId())
                .eq(Message::getMessageType, 1));
        for (Message message : messages) {
            if (message.getChatType() == 0 && message.getChatKey().matches("^P:" + userId + ":.*|^P:.*:" + userId + "$")) return;
            if (message.getChatType() == 1 && memberMapper.selectCount(new LambdaQueryWrapper<GroupMember>()
                    .eq(GroupMember::getGroupId, message.getTargetId())
                    .eq(GroupMember::getUserId, userId)) > 0) return;
        }
        throw new BusinessException("FILE_ACCESS_DENIED", "无权访问该文件资源", HttpStatus.FORBIDDEN);
    }

    private FileResource findByHash(String hash) {
        return fileMapper.selectOne(new LambdaQueryWrapper<FileResource>()
                .eq(FileResource::getFileHash, hash)
                .last("LIMIT 1"));
    }

    private void grantAccess(long resourceId, long userId) {
        if (accessMapper.selectCount(new LambdaQueryWrapper<FileResourceAccess>()
                .eq(FileResourceAccess::getResourceId, resourceId)
                .eq(FileResourceAccess::getUserId, userId)) > 0) return;
        LocalDateTime now = LocalDateTime.now();
        FileResourceAccess access = new FileResourceAccess();
        access.setResourceId(resourceId);
        access.setUserId(userId);
        access.setCreatedTime(now);
        access.setUpdatedTime(now);
        accessMapper.insert(access);
    }

    private Map<Object, Object> requireSession(long userId, String sessionId) {
        if (sessionId == null || !sessionId.matches("^[0-9a-fA-F-]{36}$")) {
            throw new BusinessException("UPLOAD_NOT_FOUND", "上传任务不存在或已过期", HttpStatus.NOT_FOUND);
        }
        Map<Object, Object> state = redisTemplate.opsForHash().entries(uploadKey(sessionId));
        if (state.isEmpty()) throw new BusinessException("UPLOAD_NOT_FOUND", "上传任务不存在或已过期", HttpStatus.NOT_FOUND);
        if (!Long.toString(userId).equals(value(state, "userId"))) {
            throw new BusinessException("FORBIDDEN", "无权操作该上传任务", HttpStatus.FORBIDDEN);
        }
        return state;
    }

    private List<Integer> uploadedParts(Map<Object, Object> state) {
        return state.keySet().stream()
                .map(String::valueOf)
                .filter(key -> key.startsWith("part:"))
                .map(key -> Integer.parseInt(key.substring(5)))
                .sorted()
                .toList();
    }

    private List<PartETag> partTags(Map<Object, Object> state) {
        List<PartETag> result = new ArrayList<>();
        state.forEach((key, value) -> {
            String field = String.valueOf(key);
            if (field.startsWith("part:")) result.add(new PartETag(
                    Integer.parseInt(field.substring(5)), String.valueOf(value)));
        });
        result.sort(Comparator.comparingInt(PartETag::getPartNumber));
        return result;
    }

    private String calculateRemoteSha256(String objectKey) {
        try (OSSObject object = ossClient.getObject(bucketName, objectKey);
             InputStream input = object.getObjectContent()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) digest.update(buffer, 0, read);
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (Exception exception) {
            throw new BusinessException("FILE_VERIFY_FAILED", "文件完整性校验失败", HttpStatus.BAD_GATEWAY);
        }
    }

    private void abortOss(Map<Object, Object> state) {
        try {
            ossClient.abortMultipartUpload(new AbortMultipartUploadRequest(
                    bucketName, value(state, "objectKey"), value(state, "ossUploadId")));
        } catch (Exception ignored) {
        }
    }

    private String uploadKey(String sessionId) {
        return "chat:upload:" + sessionId;
    }

    private String value(Map<Object, Object> state, String key) {
        Object value = state.get(key);
        if (value == null) throw new BusinessException("UPLOAD_STATE_INVALID", "上传任务状态不完整", HttpStatus.CONFLICT);
        return String.valueOf(value);
    }

    private String publicUrl(String objectKey) {
        return "https://" + bucketName + "." + endpoint + "/" + objectKey;
    }
}
