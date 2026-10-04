package top.zxylearn.chatserver.vo;

public record AuthSessionResponse(
        String tokenName,
        String tokenValue,
        UserProfileResponse user) {
}
