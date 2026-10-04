package top.zxylearn.chatserver.controller;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.zxylearn.chatserver.common.ApiResponse;
import top.zxylearn.chatserver.dto.call.CreateCallRequest;
import top.zxylearn.chatserver.dto.call.UpdateCallStatusRequest;
import top.zxylearn.chatserver.service.CallManagementService;
import top.zxylearn.chatserver.vo.CallRecordResponse;

import java.util.Map;

@RestController
@RequestMapping("/api/calls")
public class CallController {
    private final CallManagementService service;
    public CallController(CallManagementService service) { this.service = service; }

    @PostMapping
    public ApiResponse<CallRecordResponse> create(@Valid @RequestBody CreateCallRequest request) {
        long calleeId;
        try { calleeId = Long.parseLong(request.calleeId()); }
        catch (NumberFormatException exception) { throw new top.zxylearn.chatserver.exception.BusinessException("INVALID_USER_ID", "用户ID格式不正确", org.springframework.http.HttpStatus.BAD_REQUEST); }
        return ApiResponse.success(service.create(StpUtil.getLoginIdAsLong(), calleeId, request.callType()));
    }

    @PatchMapping("/{callId}")
    public ApiResponse<CallRecordResponse> update(
            @PathVariable long callId, @Valid @RequestBody UpdateCallStatusRequest request) {
        return ApiResponse.success(service.update(StpUtil.getLoginIdAsLong(), callId, request.action()));
    }

    @GetMapping("/ice-servers")
    public ApiResponse<Map<String, Object>> iceServers() { return ApiResponse.success(service.iceServers()); }
}
