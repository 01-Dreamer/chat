package top.zxylearn.chatserver.controller;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.zxylearn.chatserver.common.ApiResponse;
import top.zxylearn.chatserver.service.InboxSyncService;
import top.zxylearn.chatserver.vo.InboxSyncResponse;

@RestController
@RequestMapping("/api/sync")
public class InboxSyncController {

    private final InboxSyncService syncService;

    public InboxSyncController(InboxSyncService syncService) {
        this.syncService = syncService;
    }

    @GetMapping("/inbox")
    public ApiResponse<InboxSyncResponse> sync(
            @RequestParam(defaultValue = "0") long afterSequence,
            @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.success(syncService.sync(
                StpUtil.getLoginIdAsLong(), afterSequence, limit));
    }
}
