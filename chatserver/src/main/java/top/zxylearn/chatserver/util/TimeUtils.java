package top.zxylearn.chatserver.util;

import java.time.LocalDateTime;
import java.time.ZoneId;

public final class TimeUtils {

    private static final ZoneId DATABASE_ZONE = ZoneId.of("Asia/Shanghai");

    private TimeUtils() {
    }

    public static long toEpochMilli(LocalDateTime value) {
        return value == null ? 0 : value.atZone(DATABASE_ZONE).toInstant().toEpochMilli();
    }
}
