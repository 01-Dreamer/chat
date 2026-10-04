package top.zxylearn.chatserver.vo;

import top.zxylearn.chatserver.entity.Account;

public record AccountResponse(String id, String userId, String balance, boolean payPasswordSet, int status) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId().toString(), account.getUserId().toString(),
                account.getBalance().toPlainString(), account.getPayPasswordHash() != null,
                account.getStatus());
    }
}
