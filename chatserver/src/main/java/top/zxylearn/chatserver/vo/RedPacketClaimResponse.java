package top.zxylearn.chatserver.vo;

public record RedPacketClaimResponse(
        RedPacketResponse redPacket,
        String amount,
        boolean alreadyReceived,
        boolean expired) {
}
