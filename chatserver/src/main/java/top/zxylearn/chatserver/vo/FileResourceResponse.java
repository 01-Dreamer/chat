package top.zxylearn.chatserver.vo;

import top.zxylearn.chatserver.entity.FileResource;

import static top.zxylearn.chatserver.util.TimeUtils.toEpochMilli;

public record FileResourceResponse(
        String id,
        String uploaderId,
        int resourceType,
        String fileName,
        long fileSize,
        String mimeType,
        String fileHash,
        String fileUrl,
        Integer duration,
        long createdTime,
        long updatedTime) {

    public static FileResourceResponse from(FileResource resource) {
        return new FileResourceResponse(
                resource.getId().toString(),
                resource.getUploaderId().toString(),
                resource.getResourceType(),
                resource.getFileName(),
                resource.getFileSize(),
                resource.getMimeType(),
                resource.getFileHash(),
                resource.getFileUrl(),
                resource.getDuration(),
                toEpochMilli(resource.getCreatedTime()),
                toEpochMilli(resource.getUpdatedTime()));
    }
}
