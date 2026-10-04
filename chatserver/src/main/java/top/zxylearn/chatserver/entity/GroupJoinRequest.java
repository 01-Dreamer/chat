package top.zxylearn.chatserver.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`group_join_request`")
public class GroupJoinRequest extends BaseEntity {

    private Long groupId;
    private Long userId;
    private String message;
    private Integer status;
    private Long reviewerId;
}
