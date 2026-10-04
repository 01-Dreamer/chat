package top.zxylearn.chatserver.vo;

import top.zxylearn.chatserver.entity.Account;
import top.zxylearn.chatserver.entity.User;

import java.math.BigDecimal;
import static top.zxylearn.chatserver.util.TimeUtils.toEpochMilli;

public record UserProfileResponse(
        String id,
        String username,
        String nickname,
        String avatarUrl,
        int status,
        String balance,
        long createdTime,
        long updatedTime) {

    public static UserProfileResponse from(User user, Account account) {
        BigDecimal balance = account == null || account.getBalance() == null
                ? BigDecimal.ZERO
                : account.getBalance();
        return new UserProfileResponse(
                user.getId().toString(),
                user.getUsername(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getStatus(),
                balance.toPlainString(),
                toEpochMilli(user.getCreatedTime()),
                toEpochMilli(user.getUpdatedTime()));
    }
}
