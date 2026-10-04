package top.zxylearn.chatserver.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`user_inbox`")
public class UserInbox extends BaseEntity {

    private Long userId;
    private Long eventId;
    private Long sequence;
}
