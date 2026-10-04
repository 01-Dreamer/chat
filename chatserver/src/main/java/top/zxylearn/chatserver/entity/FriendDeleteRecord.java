package top.zxylearn.chatserver.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`friend_delete_record`")
public class FriendDeleteRecord extends BaseEntity {

    private Long operatorId;
    private Long friendId;
}
