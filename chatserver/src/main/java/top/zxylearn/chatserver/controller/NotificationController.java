package top.zxylearn.chatserver.controller;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.zxylearn.chatserver.common.ApiResponse;
import top.zxylearn.chatserver.service.NotificationCenterService;
import top.zxylearn.chatserver.vo.NotificationResponse;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationCenterService service;
    public NotificationController(NotificationCenterService service) { this.service = service; }

    @GetMapping
    public ApiResponse<List<NotificationResponse>> list(@RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.success(service.list(StpUtil.getLoginIdAsLong(), limit));
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<NotificationResponse> markRead(@PathVariable long id) {
        return ApiResponse.success(service.markRead(StpUtil.getLoginIdAsLong(), id));
    }
}
