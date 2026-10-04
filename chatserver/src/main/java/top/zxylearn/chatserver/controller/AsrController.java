package top.zxylearn.chatserver.controller;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.zxylearn.chatserver.common.ApiResponse;
import top.zxylearn.chatserver.service.AsrService;

import java.util.Map;

@RestController
@RequestMapping("/api/asr")
public class AsrController {

    private final AsrService asrService;

    public AsrController(AsrService asrService) {
        this.asrService = asrService;
    }

    @PostMapping("/{resourceId}")
    public ApiResponse<Map<String, String>> transcribe(@PathVariable long resourceId) {
        return ApiResponse.success(Map.of(
                "text", asrService.transcribe(StpUtil.getLoginIdAsLong(), resourceId)));
    }
}
