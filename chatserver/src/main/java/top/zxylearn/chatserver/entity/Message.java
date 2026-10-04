package top.zxylearn.chatserver.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`message`")
public class Message extends BaseEntity {

    private String clientMessageId;
    private String chatKey;
    private Long senderId;
    private Integer chatType;
    private Long targetId;
    private Integer messageType;
    private String content;
    private Long referenceId;
    private Long replyMessageId;
    private Integer status;
    private Long recallOperatorId;
    private LocalDateTime recalledTime;
}
