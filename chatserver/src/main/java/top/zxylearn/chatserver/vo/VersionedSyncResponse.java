package top.zxylearn.chatserver.vo;

import java.util.List;

public record VersionedSyncResponse<T>(String version, boolean changed, List<T> items) {
}
