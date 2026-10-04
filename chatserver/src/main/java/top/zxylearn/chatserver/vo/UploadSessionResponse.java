package top.zxylearn.chatserver.vo;

import java.util.List;

public record UploadSessionResponse(
        boolean instant,
        String uploadSessionId,
        long partSize,
        List<Integer> uploadedParts,
        FileResourceResponse resource) {
}
