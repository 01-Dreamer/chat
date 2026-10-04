package top.zxylearn.chatserver.vo;

import top.zxylearn.chatserver.entity.CallRecord;

import static top.zxylearn.chatserver.util.TimeUtils.toEpochMilli;

public record CallRecordResponse(
        String id, String callerId, String calleeId, int callType, int status,
        Long startTime, Long endTime, int duration, long createdTime, long updatedTime) {
    public static CallRecordResponse from(CallRecord value) {
        return new CallRecordResponse(
                value.getId().toString(), value.getCallerId().toString(), value.getCalleeId().toString(),
                value.getCallType(), value.getStatus(),
                value.getStartTime() == null ? null : toEpochMilli(value.getStartTime()),
                value.getEndTime() == null ? null : toEpochMilli(value.getEndTime()),
                value.getDuration(), toEpochMilli(value.getCreatedTime()), toEpochMilli(value.getUpdatedTime()));
    }
}
