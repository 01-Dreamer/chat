package top.zxylearn.chatserver.controller;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.zxylearn.chatserver.common.ApiResponse;
import top.zxylearn.chatserver.dto.ai.SmartReplyRequest;
import top.zxylearn.chatserver.dto.ai.TranslateRequest;
import top.zxylearn.chatserver.service.AiAssistantService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiAssistantService aiService;

    public AiController(AiAssistantService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/translate")
    public ApiResponse<Map<String, String>> translate(@Valid @RequestBody TranslateRequest request) {
        return ApiResponse.success(Map.of("translatedText", aiService.translate(
                StpUtil.getLoginIdAsLong(), request.text(), request.targetLanguage())));
    }

    @PostMapping("/smart-replies")
    public ApiResponse<Map<String, List<String>>> smartReplies(@Valid @RequestBody SmartReplyRequest request) {
        return ApiResponse.success(Map.of("suggestions", aiService.smartReplies(
                StpUtil.getLoginIdAsLong(), request.messages())));
    }
}
