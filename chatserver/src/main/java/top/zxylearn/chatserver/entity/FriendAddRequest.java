package top.zxylearn.chatserver.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`friend_add_request`")
public class FriendAddRequest extends BaseEntity {

    private Long senderId;
    private Long receiverId;
    private String message;
    private Integer status;
}
