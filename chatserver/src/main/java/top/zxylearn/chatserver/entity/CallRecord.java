package top.zxylearn.chatserver.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`call_record`")
public class CallRecord extends BaseEntity {

    private Long callerId;
    private Long calleeId;
    private Integer callType;
    private Integer status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer duration;
}
