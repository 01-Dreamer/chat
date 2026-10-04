package top.zxylearn.chatserver.vo;

import java.util.List;

public record InboxSyncResponse(
        List<InboxEventResponse> items,
        long lastSequence,
        boolean hasMore) {
}
