package top.zxylearn.chatserver.vo;

import top.zxylearn.chatserver.entity.AccountTransaction;

import static top.zxylearn.chatserver.util.TimeUtils.toEpochMilli;

public record TransactionResponse(
        String id, String clientTransactionId, String fromUserId, String toUserId,
        int transactionType, String amount, String referenceId, int status, long createdTime) {
    public static TransactionResponse from(AccountTransaction value) {
        return new TransactionResponse(
                value.getId().toString(), value.getClientTransactionId(), id(value.getFromUserId()), id(value.getToUserId()),
                value.getTransactionType(), value.getAmount().toPlainString(), id(value.getReferenceId()),
                value.getStatus(), toEpochMilli(value.getCreatedTime()));
    }
    private static String id(Long value) { return value == null ? null : value.toString(); }
}
