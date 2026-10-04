package top.zxylearn.chatserver.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`notification`")
public class Notification extends BaseEntity {

    private Long userId;
    private Integer notificationType;
    private Long referenceId;
    private String title;
    private String content;
    private Integer isRead;
    private LocalDateTime readTime;
}
