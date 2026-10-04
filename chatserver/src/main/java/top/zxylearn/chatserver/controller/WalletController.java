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
import top.zxylearn.chatserver.dto.wallet.CreateRedPacketRequest;
import top.zxylearn.chatserver.dto.wallet.SetPayPasswordRequest;
import top.zxylearn.chatserver.service.WalletService;
import top.zxylearn.chatserver.vo.AccountResponse;
import top.zxylearn.chatserver.vo.RedPacketClaimResponse;
import top.zxylearn.chatserver.vo.RedPacketResponse;

@RestController
@RequestMapping("/api")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping("/wallet")
    public ApiResponse<AccountResponse> account() {
        return ApiResponse.success(walletService.account(StpUtil.getLoginIdAsLong()));
    }

    @PatchMapping("/wallet/pay-password")
    public ApiResponse<AccountResponse> setPayPassword(@Valid @RequestBody SetPayPasswordRequest request) {
        return ApiResponse.success(walletService.setPayPassword(
                StpUtil.getLoginIdAsLong(), request.oldPassword(), request.newPassword()));
    }

    @PostMapping("/red-packets")
    public ApiResponse<RedPacketResponse> createRedPacket(@Valid @RequestBody CreateRedPacketRequest request) {
        return ApiResponse.success(walletService.createRedPacket(StpUtil.getLoginIdAsLong(), request));
    }

    @GetMapping("/red-packets/{packetId}")
    public ApiResponse<RedPacketResponse> getRedPacket(@PathVariable long packetId) {
        return ApiResponse.success(walletService.getRedPacket(StpUtil.getLoginIdAsLong(), packetId));
    }

    @PostMapping("/red-packets/{packetId}/claim")
    public ApiResponse<RedPacketClaimResponse> claim(@PathVariable long packetId) {
        return ApiResponse.success(walletService.claim(StpUtil.getLoginIdAsLong(), packetId));
    }
}
