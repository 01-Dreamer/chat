package top.zxylearn.chatserver.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`group_member`")
public class GroupMember extends BaseEntity {

    private Long groupId;
    private Long userId;
    private Integer role;
    private String nickname;
}
