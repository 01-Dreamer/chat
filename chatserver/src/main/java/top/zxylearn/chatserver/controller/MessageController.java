package top.zxylearn.chatserver.controller;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.zxylearn.chatserver.common.ApiResponse;
import top.zxylearn.chatserver.mq.RealtimeEvent;
import top.zxylearn.chatserver.mq.RealtimeEventPublisher;
import top.zxylearn.chatserver.service.MessagePersistenceService;
import top.zxylearn.chatserver.vo.MessageResponse;

import java.util.List;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessagePersistenceService messageService;
    private final RealtimeEventPublisher realtimePublisher;

    public MessageController(
            MessagePersistenceService messageService,
            RealtimeEventPublisher realtimePublisher) {
        this.messageService = messageService;
        this.realtimePublisher = realtimePublisher;
    }

    @GetMapping
    public ApiResponse<List<MessageResponse>> history(
            @RequestParam String chatKey,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.success(messageService.history(
                StpUtil.getLoginIdAsLong(), chatKey, beforeId, limit));
    }

    @PostMapping("/{messageId}/recall")
    public ApiResponse<MessageResponse> recall(@PathVariable long messageId) {
        MessagePersistenceService.PersistedMessage result = messageService.recall(
                StpUtil.getLoginIdAsLong(), messageId);
        realtimePublisher.publish(RealtimeEvent.businessEvent(
                "MESSAGE_RECALLED", result.targetSequences(), result.message()));
        return ApiResponse.success(result.message());
    }
}
