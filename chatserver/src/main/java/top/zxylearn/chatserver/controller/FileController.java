package top.zxylearn.chatserver.controller;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import top.zxylearn.chatserver.common.ApiResponse;
import top.zxylearn.chatserver.dto.file.InitializeUploadRequest;
import top.zxylearn.chatserver.service.OssFileService;
import top.zxylearn.chatserver.service.ActionRateLimiter;
import org.springframework.beans.factory.annotation.Value;
import top.zxylearn.chatserver.vo.FileResourceResponse;
import top.zxylearn.chatserver.vo.UploadSessionResponse;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class FileController {

    private final OssFileService fileService;
    private final ActionRateLimiter rateLimiter;
    private final int initializeLimit;
    private final int initializeWindow;

    public FileController(OssFileService fileService, ActionRateLimiter rateLimiter,
                          @Value("${chat.rate-limit.file-init.max-requests}") int initializeLimit,
                          @Value("${chat.rate-limit.file-init.window-seconds}") int initializeWindow) {
        this.fileService = fileService;
        this.rateLimiter = rateLimiter;
        this.initializeLimit = initializeLimit;
        this.initializeWindow = initializeWindow;
    }

    @PostMapping("/files/uploads")
    public ApiResponse<UploadSessionResponse> initialize(@Valid @RequestBody InitializeUploadRequest request) {
        long userId = StpUtil.getLoginIdAsLong();
        rateLimiter.check("file-init", userId, initializeLimit, initializeWindow);
        return ApiResponse.success(fileService.initialize(userId, request));
    }

    @GetMapping("/files/uploads/{sessionId}")
    public ApiResponse<UploadSessionResponse> status(@PathVariable String sessionId) {
        return ApiResponse.success(fileService.status(StpUtil.getLoginIdAsLong(), sessionId));
    }

    @PostMapping("/files/uploads/{sessionId}/parts/{partNumber}")
    public ApiResponse<Map<String, Integer>> uploadPart(
            @PathVariable String sessionId,
            @PathVariable int partNumber,
            @RequestPart("file") MultipartFile file) {
        int uploaded = fileService.uploadPart(StpUtil.getLoginIdAsLong(), sessionId, partNumber, file);
        return ApiResponse.success(Map.of("partNumber", uploaded));
    }

    @PostMapping("/files/uploads/{sessionId}/complete")
    public ApiResponse<FileResourceResponse> complete(@PathVariable String sessionId) {
        return ApiResponse.success(fileService.complete(StpUtil.getLoginIdAsLong(), sessionId));
    }

    @DeleteMapping("/files/uploads/{sessionId}")
    public ApiResponse<Void> abort(@PathVariable String sessionId) {
        fileService.abort(StpUtil.getLoginIdAsLong(), sessionId);
        return ApiResponse.success(null);
    }

    @GetMapping("/files/{resourceId}")
    public ApiResponse<FileResourceResponse> get(@PathVariable long resourceId) {
        return ApiResponse.success(fileService.get(StpUtil.getLoginIdAsLong(), resourceId));
    }

    @GetMapping("/files/{resourceId}/download-url")
    public ApiResponse<Map<String, String>> downloadUrl(@PathVariable long resourceId) {
        return ApiResponse.success(Map.of("url", fileService.signedDownloadUrl(
                StpUtil.getLoginIdAsLong(), resourceId)));
    }
}
